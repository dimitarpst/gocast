// Bindings from wrangler.toml plus secrets set with `wrangler secret put`.
declare namespace Cloudflare {
	interface GlobalProps {
		mainModule: typeof import("./index");
	}
	interface Env {
		ROOMS: DurableObjectNamespace<import("./room").SignalRoom>;
		/** "true" turns relay credentials on; anything else means STUN only. */
		RELAY_ENABLED?: string;
		/** Secret: comma-separated room ids that may receive TURN credentials. */
		ALLOWED_ROOMS?: string;
		/** Secrets from the Cloudflare TURN key. */
		TURN_KEY_ID?: string;
		TURN_KEY_API_TOKEN?: string;
	}
}

interface Env extends Cloudflare.Env {}
