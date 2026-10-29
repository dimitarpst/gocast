import { exports } from "cloudflare:workers";

export const PC_KEY = "pc-host-key-0123456789abcdefghijklmnop";
export const OTHER_KEY = "other-host-key-0123456789abcdefghijklm";

let counter = 0;

/** A fresh room id per test so rooms never share state. */
export function newRoomId(prefix = "room"): string {
	counter += 1;
	return `${prefix}${String(counter).padStart(4, "0")}${crypto.randomUUID().replaceAll("-", "")}`.slice(0, 32);
}

export function connectRequest(roomId: string, role: string, hostKey?: string): Promise<Response> {
	const headers: Record<string, string> = { Upgrade: "websocket" };
	if (hostKey) headers.Authorization = `Bearer ${hostKey}`;
	return exports.default.fetch(`https://signal.test/room/${roomId}?role=${role}`, { headers });
}

/** Test side of one WebSocket: queues text messages and the close event. */
export class Peer {
	private readonly queue: string[] = [];
	private waiter: ((message: string) => void) | null = null;
	readonly closed: Promise<{ code: number; reason: string }>;

	constructor(readonly ws: WebSocket) {
		ws.accept();
		ws.addEventListener("message", (event) => {
			const text = typeof event.data === "string" ? event.data : "<binary>";
			if (this.waiter) {
				const resolve = this.waiter;
				this.waiter = null;
				resolve(text);
			} else {
				this.queue.push(text);
			}
		});
		this.closed = new Promise((resolve) => {
			ws.addEventListener("close", (event) => resolve({ code: event.code, reason: event.reason }));
		});
	}

	static async connect(roomId: string, role: "pc" | "phone", hostKey?: string): Promise<Peer> {
		const response = await connectRequest(roomId, role, hostKey);
		if (response.status !== 101 || !response.webSocket) {
			throw new Error(`connect ${role} failed: HTTP ${response.status} ${await response.text()}`);
		}
		return new Peer(response.webSocket);
	}

	send(message: string | object): void {
		this.ws.send(typeof message === "string" ? message : JSON.stringify(message));
	}

	next(timeoutMs = 2000): Promise<string> {
		const queued = this.queue.shift();
		if (queued !== undefined) return Promise.resolve(queued);
		return new Promise((resolve, reject) => {
			const timer = setTimeout(() => {
				this.waiter = null;
				reject(new Error("timed out waiting for a message"));
			}, timeoutMs);
			this.waiter = (message) => {
				clearTimeout(timer);
				resolve(message);
			};
		});
	}

	async nextJson(timeoutMs = 2000): Promise<Record<string, unknown>> {
		return JSON.parse(await this.next(timeoutMs)) as Record<string, unknown>;
	}

	/** Skips control messages such as phone-left until one of `type` arrives. */
	async nextOfType(type: string, timeoutMs = 2000): Promise<Record<string, unknown>> {
		for (;;) {
			const message = await this.nextJson(timeoutMs);
			if (message.type === type) return message;
		}
	}

	/** Resolves true if nothing arrives within `ms`. */
	async staysQuiet(ms = 150): Promise<boolean> {
		try {
			await this.next(ms);
			return false;
		} catch {
			return true;
		}
	}

	close(): void {
		try {
			this.ws.close(1000, "test done");
		} catch {
			// Already closed.
		}
	}
}

/** PC + phone in one room, with the phone accepted by the PC. */
export async function acceptedPair(roomId: string): Promise<{ pc: Peer; phone: Peer; phoneId: string }> {
	const pc = await Peer.connect(roomId, "pc", PC_KEY);
	const phone = await Peer.connect(roomId, "phone");
	const joined = await pc.nextJson();
	const phoneId = String(joined.phoneId);
	phone.send({ type: "hello", token: "ABCD" });
	await pc.next();
	pc.send({ type: "phone-accepted", phoneId });
	return { pc, phone, phoneId };
}
