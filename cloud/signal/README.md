# GoCast signaling Worker

Lets the phone and PC find each other across networks. It only passes the small
setup messages (hello, offer, answer, ICE). Screen and audio still go phone → PC
over WebRTC: directly when possible, through Cloudflare TURN only as a last resort.

- One Durable Object per PC "room": `wss://signal.govrse.app/room/<roomId>?role=pc|phone`.
- The PC claims its room with a host key (sent as `Authorization: Bearer …`).
  The object stores only the key's SHA-256.
- One phone at a time. The PC still checks codes and trust tokens itself.
- STUN (`stun.cloudflare.com`) for everyone. TURN credentials only for the PC of
  an allowlisted room and the phone that PC accepted.
- Idle rooms hibernate; pings are answered without waking them.

Nothing here is deployed by the repository. You run the steps below yourself.

## One-time setup

Run these from `cloud/signal/`. Node 20+ is enough.

1. Install and sign in:

   ```bash
   npm ci
   ```

   ```bash
   npx wrangler login
   ```

2. Create a TURN key: Cloudflare dashboard → **Realtime** → **TURN Server** →
   create a key. Copy its key ID and API token.

3. Store them as Worker secrets (you'll be prompted to paste each value):

   ```bash
   npx wrangler secret put TURN_KEY_ID
   ```

   ```bash
   npx wrangler secret put TURN_KEY_API_TOKEN
   ```

   Without these two secrets the Worker still works, with STUN only.

4. Deploy. `wrangler.toml` already attaches the custom domain `signal.govrse.app`
   (the `govrse.app` zone must be in the same Cloudflare account). It does not
   touch `cast.govrse.app`.

   ```bash
   npx wrangler deploy
   ```

   Check it: `https://signal.govrse.app/health` should say `gocast signal ok`.

## Allow a PC to use the relay

1. Start GoCast on the PC. It logs `Remote room id: <id>` at startup. The same id
   is saved as `roomId` in `%LOCALAPPDATA%\gocast\identity.json`.
2. Set the allowlist secret to all allowed room ids, comma-separated. This
   replaces the old list, so include every PC:

   ```bash
   npx wrangler secret put ALLOWED_ROOMS
   ```

   Example value: `Xq3…roomOne,Ab9…roomTwo`

Rooms not on the list can still connect (direct only, STUN).

Keep room ids private: anyone with a room id can knock on that PC (it still
needs the pairing code or a trust token). That's why the list is a secret and
not in `wrangler.toml`.

## Relay kill switch

Relay egress is free up to 1,000 GB/month, then billed. To stop issuing relay
credentials at once:

```bash
npx wrangler deploy --var RELAY_ENABLED:false
```

To keep it off, also set `RELAY_ENABLED = "false"` in `wrangler.toml`. Anything
other than `"true"` means STUN only. Credentials already issued last up to 4 hours.

## Messages

Existing GoCast messages pass through unchanged. The Worker adds only these:

| From | Message | Meaning |
| --- | --- | --- |
| Worker → PC | `{"type":"phone-joined","phoneId":"…"}` | A phone connected; its `hello` follows. |
| Worker → PC | `{"type":"phone-left","phoneId":"…"}` | That phone disconnected. |
| PC → Worker | `{"type":"phone-accepted","phoneId":"…"}` | Hello was valid. Replaces any earlier phone. |
| PC → Worker | `{"type":"phone-rejected","phoneId":"…"}` | Hello was invalid; the phone is closed. |
| PC or phone → Worker | `{"type":"get-ice-servers"}` | Reply: `{"type":"ice-servers","iceServers":[…]}` |
| PC or phone → Worker | `{"type":"ping"}` | Reply: `{"type":"pong"}` (answered without waking the room). |

Before the PC accepts it, a phone may only send `hello`.

Close codes: 4000 PC replaced, 4001 waiting phone replaced, 4002 another phone
accepted, 4003 rejected by PC, 4004 PC offline. A phone connecting while the PC
is offline gets HTTP 503.

## Tests

```bash
npm test
```

```bash
npm run typecheck
```

Tests run locally in the Workers runtime (Miniflare). The TURN API is faked;
nothing is sent to Cloudflare.
