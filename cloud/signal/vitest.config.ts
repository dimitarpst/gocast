import { cloudflareTest } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

// Fake TURN API: returns the documented response shape, including a port-53 URL.
const turnResponse = {
	iceServers: [
		{ urls: ["stun:stun.cloudflare.com:3478", "stun:stun.cloudflare.com:53"] },
		{
			urls: [
				"turn:turn.cloudflare.com:3478?transport=udp",
				"turn:turn.cloudflare.com:53?transport=udp",
				"turns:turn.cloudflare.com:443?transport=tcp",
			],
			username: "test-user",
			credential: "test-credential",
		},
	],
};

export default defineConfig({
	plugins: [
		cloudflareTest({
			wrangler: { configPath: "./wrangler.toml" },
			miniflare: {
				bindings: {
					ALLOWED_ROOMS: "allowedRoomAAAAAAAAAAA, allowedRoomBBBBBBBBBBB",
					TURN_KEY_ID: "test-key-id",
					TURN_KEY_API_TOKEN: "test-api-token",
				},
				outboundService: (request: Request) => {
					const url = new URL(request.url);
					if (
						url.hostname === "rtc.live.cloudflare.com" &&
						url.pathname === "/v1/turn/keys/test-key-id/credentials/generate-ice-servers" &&
						request.headers.get("Authorization") === "Bearer test-api-token"
					) {
						return Response.json(turnResponse, { status: 201 });
					}
					return new Response("unexpected outbound request", { status: 500 });
				},
			},
		}),
	],
});
