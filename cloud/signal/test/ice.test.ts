import { describe, expect, it } from "vitest";
import {
	CREDENTIAL_TTL_SECONDS,
	iceServersFor,
	isAllowedRoom,
	STUN_ONLY,
	withoutPort53,
} from "../src/ice";

const configured = {
	RELAY_ENABLED: "true",
	TURN_KEY_ID: "key-id",
	TURN_KEY_API_TOKEN: "api-token",
} as Env;

const turnBody = {
	iceServers: [
		{ urls: ["stun:stun.cloudflare.com:3478"] },
		{
			urls: ["turn:turn.cloudflare.com:3478?transport=udp", "turn:turn.cloudflare.com:53?transport=udp"],
			username: "user",
			credential: "secret",
		},
	],
};

function fakeFetch(response: () => Response | Promise<Response>) {
	const calls: { url: string; init: RequestInit | undefined }[] = [];
	const fetcher = (async (input: RequestInfo | URL, init?: RequestInit) => {
		calls.push({ url: String(input), init });
		return response();
	}) as typeof fetch;
	return { fetcher, calls };
}

describe("iceServersFor", () => {
	it("asks Cloudflare for short-lived credentials and drops port 53", async () => {
		const { fetcher, calls } = fakeFetch(() => Response.json(turnBody, { status: 201 }));
		const servers = await iceServersFor(configured, true, fetcher);

		expect(calls).toHaveLength(1);
		expect(calls[0]!.url).toBe(
			"https://rtc.live.cloudflare.com/v1/turn/keys/key-id/credentials/generate-ice-servers",
		);
		expect(calls[0]!.init?.method).toBe("POST");
		expect(new Headers(calls[0]!.init?.headers).get("Authorization")).toBe("Bearer api-token");
		expect(JSON.parse(String(calls[0]!.init?.body))).toEqual({ ttl: CREDENTIAL_TTL_SECONDS });
		expect(CREDENTIAL_TTL_SECONDS).toBeLessThanOrEqual(4 * 60 * 60);
		expect(servers).toEqual([
			{ urls: ["stun:stun.cloudflare.com:3478"] },
			{ urls: ["turn:turn.cloudflare.com:3478?transport=udp"], username: "user", credential: "secret" },
		]);
	});

	it("returns STUN only without asking when relay is not allowed", async () => {
		const { fetcher, calls } = fakeFetch(() => Response.json(turnBody, { status: 201 }));
		expect(await iceServersFor(configured, false, fetcher)).toEqual(STUN_ONLY);
		expect(calls).toHaveLength(0);
	});

	it("returns STUN only when the kill switch is off", async () => {
		const { fetcher, calls } = fakeFetch(() => Response.json(turnBody, { status: 201 }));
		for (const value of ["false", "", "no", undefined]) {
			const env = { ...configured, RELAY_ENABLED: value } as Env;
			expect(await iceServersFor(env, true, fetcher)).toEqual(STUN_ONLY);
		}
		expect(calls).toHaveLength(0);
	});

	it("returns STUN only when the TURN secrets are missing", async () => {
		const { fetcher, calls } = fakeFetch(() => Response.json(turnBody, { status: 201 }));
		expect(await iceServersFor({ ...configured, TURN_KEY_ID: undefined } as Env, true, fetcher)).toEqual(STUN_ONLY);
		expect(await iceServersFor({ ...configured, TURN_KEY_API_TOKEN: " " } as Env, true, fetcher)).toEqual(STUN_ONLY);
		expect(calls).toHaveLength(0);
	});

	it("falls back to STUN when Cloudflare fails", async () => {
		const failing = [
			() => new Response("nope", { status: 401 }),
			() => new Response("not json", { status: 201 }),
			() => Response.json({ unexpected: true }, { status: 201 }),
			() => Promise.reject(new Error("network down")),
		];
		for (const response of failing) {
			const { fetcher } = fakeFetch(response);
			expect(await iceServersFor(configured, true, fetcher)).toEqual(STUN_ONLY);
		}
	});
});

describe("isAllowedRoom", () => {
	it("matches whole comma-separated room ids", () => {
		expect(isAllowedRoom("roomA, roomB ,roomC", "roomB")).toBe(true);
		expect(isAllowedRoom("roomA,roomB", "room")).toBe(false);
		expect(isAllowedRoom("roomA,,", "")).toBe(false);
		expect(isAllowedRoom(undefined, "roomA")).toBe(false);
		expect(isAllowedRoom("", "roomA")).toBe(false);
	});
});

describe("withoutPort53", () => {
	it("removes port-53 URLs and empty servers", () => {
		expect(
			withoutPort53([
				{ urls: ["stun:stun.cloudflare.com:53"] },
				{ urls: ["turn:a:53?transport=udp", "turn:a:5349?transport=tcp", "turns:a:443?transport=tcp"] },
			]),
		).toEqual([{ urls: ["turn:a:5349?transport=tcp", "turns:a:443?transport=tcp"] }]);
	});
});
