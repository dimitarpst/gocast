// ICE servers for one peer. Cloudflare STUN is free; TURN relay egress is billed
// past the free allowance, so relay credentials are opt-in per room.

export interface IceServer {
	urls: string[];
	username?: string;
	credential?: string;
}

export const STUN_ONLY: IceServer[] = [{ urls: ["stun:stun.cloudflare.com:3478"] }];

/** Credentials are minted per session, so a leaked one stops working soon. */
export const CREDENTIAL_TTL_SECONDS = 4 * 60 * 60;

export function relayEnabled(env: Env): boolean {
	return (
		env.RELAY_ENABLED?.trim().toLowerCase() === "true" &&
		Boolean(env.TURN_KEY_ID?.trim()) &&
		Boolean(env.TURN_KEY_API_TOKEN?.trim())
	);
}

export function isAllowedRoom(allowedRooms: string | undefined, roomId: string): boolean {
	if (!allowedRooms) return false;
	return allowedRooms
		.split(",")
		.map((room) => room.trim())
		.some((room) => room.length > 0 && room === roomId);
}

/** Browsers block port 53, so those URLs only add a timeout. */
export function withoutPort53(servers: IceServer[]): IceServer[] {
	return servers
		.map((server) => ({
			...server,
			urls: server.urls.filter((url) => !/:53(\?|$)/.test(url)),
		}))
		.filter((server) => server.urls.length > 0);
}

/** Relay credentials when allowed; STUN only otherwise or when Cloudflare fails. */
export async function iceServersFor(
	env: Env,
	relayAllowed: boolean,
	fetcher: typeof fetch = fetch,
): Promise<IceServer[]> {
	if (!relayAllowed || !relayEnabled(env)) return STUN_ONLY;
	const keyId = encodeURIComponent(env.TURN_KEY_ID!.trim());
	try {
		const response = await fetcher(
			`https://rtc.live.cloudflare.com/v1/turn/keys/${keyId}/credentials/generate-ice-servers`,
			{
				method: "POST",
				headers: {
					Authorization: `Bearer ${env.TURN_KEY_API_TOKEN!.trim()}`,
					"Content-Type": "application/json",
				},
				body: JSON.stringify({ ttl: CREDENTIAL_TTL_SECONDS }),
			},
		);
		if (!response.ok) {
			console.warn(`TURN credentials failed: HTTP ${response.status}`);
			return STUN_ONLY;
		}
		const servers = parseIceServers(await response.json());
		return servers.length > 0 ? servers : STUN_ONLY;
	} catch (error) {
		console.warn(`TURN credentials failed: ${String(error)}`);
		return STUN_ONLY;
	}
}

function parseIceServers(body: unknown): IceServer[] {
	const list = (body as { iceServers?: unknown })?.iceServers;
	if (!Array.isArray(list)) return [];
	const servers: IceServer[] = [];
	for (const entry of list) {
		const raw = (entry as { urls?: unknown })?.urls;
		const urls = (Array.isArray(raw) ? raw : [raw]).filter(
			(url): url is string => typeof url === "string",
		);
		const { username, credential } = entry as { username?: unknown; credential?: unknown };
		servers.push({
			urls,
			...(typeof username === "string" ? { username } : {}),
			...(typeof credential === "string" ? { credential } : {}),
		});
	}
	return withoutPort53(servers);
}
