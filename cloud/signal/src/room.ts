import { DurableObject } from "cloudflare:workers";
import { iceServersFor, isAllowedRoom } from "./ice";

// One object per PC room. All state lives in WebSocket tags/attachments and
// storage, so the object can hibernate between messages.

type Attachment =
	| { role: "pc"; roomId: string }
	| { role: "phone"; roomId: string; phoneId: string; accepted: boolean };

const PING = '{"type":"ping"}';
const PONG = '{"type":"pong"}';
const HOST_KEY_HASH = "hostKeyHash";
const MAX_MESSAGE_CHARS = 64 * 1024;

// Close codes the apps can show or log.
export const CLOSE_REPLACED = 4000;
export const CLOSE_PHONE_REPLACED = 4001;
export const CLOSE_OTHER_PHONE_ACCEPTED = 4002;
export const CLOSE_REJECTED = 4003;
export const CLOSE_PC_OFFLINE = 4004;

export class SignalRoom extends DurableObject<Env> {
	constructor(ctx: DurableObjectState, env: Env) {
		super(ctx, env);
		// Answered by the runtime without waking the object.
		ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair(PING, PONG));
	}

	async fetch(request: Request): Promise<Response> {
		const url = new URL(request.url);
		const roomId = url.pathname.split("/")[2] ?? "";
		switch (url.searchParams.get("role")) {
			case "pc":
				return this.connectPc(request, roomId);
			case "phone":
				return this.connectPhone(roomId);
			default:
				return new Response("role must be pc or phone", { status: 400 });
		}
	}

	private async connectPc(request: Request, roomId: string): Promise<Response> {
		const hostKey = bearerToken(request);
		if (!hostKey || hostKey.length < 32 || hostKey.length > 128) {
			return new Response("Missing host key", { status: 401 });
		}
		const hash = await sha256(hostKey);
		// The first PC claims the room; only the key's hash is stored.
		const stored = this.ctx.storage.kv.get<string>(HOST_KEY_HASH);
		if (stored === undefined) {
			this.ctx.storage.kv.put(HOST_KEY_HASH, hash);
			console.log(`room ${roomId} claimed`);
		} else if (!sameHash(stored, hash)) {
			return new Response("Room belongs to another PC", { status: 403 });
		}

		// One PC at a time: a reconnect replaces the old socket and its phones.
		for (const old of this.ctx.getWebSockets("pc")) {
			closeQuietly(old, CLOSE_REPLACED, "Replaced by a new PC connection");
		}
		this.dropAllPhones(CLOSE_PC_OFFLINE, "PC reconnected");

		const { 0: client, 1: server } = new WebSocketPair();
		this.ctx.acceptWebSocket(server, ["pc"]);
		server.serializeAttachment({ role: "pc", roomId } satisfies Attachment);
		return new Response(null, { status: 101, webSocket: client });
	}

	private connectPhone(roomId: string): Response {
		const pc = this.pcSocket();
		if (!pc) return new Response("PC is offline", { status: 503 });

		// Only the newest phone waits for the PC's decision.
		for (const ws of this.ctx.getWebSockets("phone")) {
			if (!(attachment(ws) as { accepted?: boolean }).accepted) {
				this.dropPhone(ws, CLOSE_PHONE_REPLACED, "Another phone is connecting");
			}
		}

		const phoneId = crypto.randomUUID();
		const { 0: client, 1: server } = new WebSocketPair();
		this.ctx.acceptWebSocket(server, ["phone", phoneTag(phoneId)]);
		server.serializeAttachment({
			role: "phone",
			roomId,
			phoneId,
			accepted: false,
		} satisfies Attachment);
		pc.send(JSON.stringify({ type: "phone-joined", phoneId }));
		return new Response(null, { status: 101, webSocket: client });
	}

	async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
		if (typeof message !== "string") return;
		if (message.length > MAX_MESSAGE_CHARS) {
			ws.close(1009, "Message too large");
			return;
		}
		const from = attachment(ws);
		const parsed = parse(message);
		if (from.role === "pc") {
			await this.fromPc(ws, from.roomId, message, parsed);
		} else {
			await this.fromPhone(ws, from, message, parsed);
		}
	}

	private async fromPc(
		ws: WebSocket,
		roomId: string,
		message: string,
		parsed: Record<string, unknown> | null,
	): Promise<void> {
		switch (parsed?.type) {
			case "get-ice-servers":
				ws.send(await this.iceServersMessage(isAllowedRoom(this.env.ALLOWED_ROOMS, roomId)));
				return;
			case "phone-accepted":
				this.acceptPhone(String(parsed.phoneId ?? ""));
				return;
			case "phone-rejected":
				for (const phone of this.ctx.getWebSockets(phoneTag(String(parsed.phoneId ?? "")))) {
					this.dropPhone(phone, CLOSE_REJECTED, "Rejected by PC");
				}
				return;
			default:
				// Existing GoCast messages pass through unchanged.
				this.acceptedPhone()?.send(message);
		}
	}

	private async fromPhone(
		ws: WebSocket,
		from: Extract<Attachment, { role: "phone" }>,
		message: string,
		parsed: Record<string, unknown> | null,
	): Promise<void> {
		if (parsed?.type === "get-ice-servers") {
			const relayAllowed =
				from.accepted && isAllowedRoom(this.env.ALLOWED_ROOMS, from.roomId);
			ws.send(await this.iceServersMessage(relayAllowed));
			return;
		}
		// Before the PC accepts it, a phone may only say hello.
		if (!from.accepted && parsed?.type !== "hello") return;
		if (from.accepted && parsed?.type === "hello") return;
		this.pcSocket()?.send(message);
	}

	private acceptPhone(phoneId: string): void {
		// Accepting a phone replaces any earlier one, as on the LAN path.
		for (const ws of this.ctx.getWebSockets("phone")) {
			const phone = attachment(ws) as Extract<Attachment, { role: "phone" }>;
			if (phone.phoneId === phoneId) {
				ws.serializeAttachment({ ...phone, accepted: true } satisfies Attachment);
			} else {
				if (phone.accepted) ws.send('{"type":"session-ended"}');
				this.dropPhone(ws, CLOSE_OTHER_PHONE_ACCEPTED, "Another phone was accepted");
			}
		}
	}

	async webSocketClose(ws: WebSocket, code: number, reason: string): Promise<void> {
		this.onSocketGone(ws);
		closeQuietly(ws, code, reason);
	}

	async webSocketError(ws: WebSocket): Promise<void> {
		this.onSocketGone(ws);
	}

	private onSocketGone(ws: WebSocket): void {
		const gone = attachment(ws);
		if (gone.role === "phone") {
			this.pcSocket()?.send(JSON.stringify({ type: "phone-left", phoneId: gone.phoneId }));
		} else if (!this.ctx.getWebSockets("pc").some((pc) => pc !== ws && isOpen(pc))) {
			this.dropAllPhones(CLOSE_PC_OFFLINE, "PC went offline");
		}
	}

	private async iceServersMessage(relayAllowed: boolean): Promise<string> {
		const iceServers = await iceServersFor(this.env, relayAllowed);
		return JSON.stringify({ type: "ice-servers", iceServers });
	}

	private pcSocket(): WebSocket | undefined {
		return this.ctx.getWebSockets("pc").find(isOpen);
	}

	private acceptedPhone(): WebSocket | undefined {
		return this.ctx
			.getWebSockets("phone")
			.find((ws) => isOpen(ws) && (attachment(ws) as { accepted?: boolean }).accepted);
	}

	private dropAllPhones(code: number, reason: string): void {
		for (const ws of this.ctx.getWebSockets("phone")) this.dropPhone(ws, code, reason);
	}

	private dropPhone(ws: WebSocket, code: number, reason: string): void {
		const phone = attachment(ws) as Extract<Attachment, { role: "phone" }>;
		closeQuietly(ws, code, reason);
		this.pcSocket()?.send(JSON.stringify({ type: "phone-left", phoneId: phone.phoneId }));
	}
}

function attachment(ws: WebSocket): Attachment {
	return ws.deserializeAttachment() as Attachment;
}

function closeQuietly(ws: WebSocket, code: number, reason: string): void {
	try {
		ws.close(code, reason);
	} catch {
		// Already closed, or a reserved code such as 1005 that close() refuses.
	}
}

function phoneTag(phoneId: string): string {
	return `phone:${phoneId}`;
}

function isOpen(ws: WebSocket): boolean {
	return ws.readyState === WebSocket.OPEN;
}

function parse(message: string): Record<string, unknown> | null {
	try {
		const value: unknown = JSON.parse(message);
		return value !== null && typeof value === "object" ? (value as Record<string, unknown>) : null;
	} catch {
		return null;
	}
}

function bearerToken(request: Request): string | null {
	const header = request.headers.get("Authorization") ?? "";
	const match = /^Bearer\s+(\S+)$/i.exec(header);
	return match?.[1] ?? null;
}

async function sha256(text: string): Promise<string> {
	const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
	return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function sameHash(a: string, b: string): boolean {
	const left = new TextEncoder().encode(a);
	const right = new TextEncoder().encode(b);
	return left.length === right.length && crypto.subtle.timingSafeEqual(left, right);
}
