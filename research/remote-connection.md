# Connecting across networks (Cloudflare signaling + STUN/TURN)

Recorded: 29 October 2026. Status: proposed plan; nothing implemented, deployed, or tested.

## Goal

Phone and PC connect across any networks (PC on LAN and phone on mobile data, campus Wi-Fi with client isolation) with the lowest available latency, free, hosted only on Cloudflare.

## How GoCast connects today (source reading, 29 Oct)

Media already uses WebRTC. Only the setup around it is tied to the LAN:

| Piece | Today | LAN dependency |
| --- | --- | --- |
| Media | Phone offers H.264/H.265/VP8/VP9/AV1 video + stereo Opus (`WebRtcPublisher.kt`, stream-webrtc-android). PC answers inside the WebView2 viewer (`ViewerPage.kt`). | None in itself. |
| ICE | `RTCConfiguration(emptyList())` on Android and `iceServers: []` in the viewer. | Host candidates only, with no STUN or TURN. Works only if the phone can reach the PC's LAN IP directly. |
| Signaling | Phone opens `ws://<PC LAN IP>:8765/ws` (`SignalingSession.kt`). The PC's Ktor `SignalingHub` relays between the phone and the local viewer page. | Needs inbound TCP 8765 on the PC (firewall rule + UAC) and phone-to-PC reachability. |
| Pairing | QR `https://cast.govrse.app/join?a=<LAN IP>:8765&t=<code>`. The PC returns a UUID trust token stored on the phone with host and port. | The QR carries a LAN address. |
| Reconnect | `TrustedCastResolver` rejoins only when the trusted PC is visible over mDNS. | Multicast discovery. |

On client-isolated Wi-Fi, the TCP 8765 connection, mDNS discovery, and host-candidate media all fail. That matches the "can't reach PC / firewall" messages from `LanHealthProbe`, but it is a hypothesis: client isolation has not been confirmed on the dorm network.

## Cloudflare facts checked (29 Oct)

- **TURN:** egress (Cloudflare → client) is billed. 1,000 GB/month is free, shared with Realtime SFU, then $0.05/GB. STUN (`stun.cloudflare.com`) is free and unlimited. Credentials come from `POST rtc.live.cloudflare.com/v1/turn/keys/{id}/credentials/generate-ice-servers` and are generated server-side; the key never ships in an app. TTL is at most 48 h, with a revoke endpoint. Relays are anycast at the nearest PoP and cannot relay to private addresses. ICE restart is recommended for maintenance disruptions. [Realtime pricing](https://developers.cloudflare.com/realtime/sfu/pricing/), [TURN FAQ](https://developers.cloudflare.com/realtime/turn/faq/), [credentials](https://developers.cloudflare.com/realtime/turn/generate-credentials/)
- **Durable Objects (Free plan):** SQLite-backed objects are available. Limits are 100,000 requests/day and 13,000 GB-s/day. Incoming WebSocket messages count 20:1, and outgoing messages and protocol pings are free. Hibernating WebSockets accrue no duration. Over the limit, operations fail rather than bill. [DO pricing](https://developers.cloudflare.com/durable-objects/platform/pricing/)
- **Not confirmed:** whether enabling TURN requires a payment method on file. If one is on file, usage past 1,000 GB would bill, so the plan includes a hard budget guard.
- **Budget arithmetic:** relayed casting at the default 8 Mbps is about 3.6 GB/hour (about 270 h/month free); the 16 Mbps preset is about 7.2 GB/hour (about 135 h/month). If the dorm forces every session through relay, this is the real limit.

## Design

1. **Signaling Worker + Durable Object** on a `govrse.app` subdomain (proposed `signal.govrse.app`, so the live `cast.govrse.app` join page and app links stay untouched). There is one object per PC room. Use the WebSocket Hibernation API with auto-responded pings, so idle PCs cost nothing.
2. **The PC stays the authority.** The Worker relays the existing hello/ok/offer/answer/ice messages unchanged, and `SessionGuard` on the PC still validates codes and trust tokens. The PC claims its room with a locally generated room id and host key (the object stores only the key's hash).
3. **ICE servers:** the Worker returns Cloudflare STUN + TURN credentials (short TTL, port-53 URLs filtered) only to the PC and to a phone the PC has accepted. Both peers keep host candidates, so ICE still picks direct LAN when reachable, then direct over the internet, then relay.
4. **Signaling path:** the PC dials out (no inbound port or firewall rule needed for remote mode). The phone uses the Worker by default. LAN signaling on 8765 stays as an offline fallback during migration.
5. **Pairing:** the QR gains a room parameter. Trusted phones store the room id and reconnect through the Worker without mDNS.
6. **Honest status:** report the selected candidate pair from `getStats` as Direct (LAN), Direct (internet), or Relayed. Never infer receiver playback from local capture.
7. **Abuse and budget:** only enrolled PCs can mint relay credentials. A scheduled check stops issuing relay credentials near the monthly cap. The exact usage source (Cloudflare analytics API vs. a conservative relayed-minutes estimate) is to be confirmed.

Security note: SDP passes through the user's own Worker over WSS. DTLS still encrypts media, but signaling integrity relies on the Worker. Signing SDP with the pairing secret is a later hardening option.

## Steps (each a small, separately checked change)

1. Worker + Durable Object in `cloud/signal/` with local tests (`wrangler dev` / Workers Vitest). No app changes, no deployment.
2. `protocol/`: room id in the join link and remote-signaling message types, with compatibility tests.
3. Desktop: persistent room id/host key, an outbound Worker connection bridged into `SignalingHub`, ICE servers passed into the viewer, and the QR with a room.
4. Android: room id in `TrustedPcStore`, a remote transport in `SignalingSession`, ICE servers in `RTCConfiguration`, and trusted reconnect without mDNS.
5. Connection-type indicator (LAN / internet / relayed) on both sides, plus logs.
6. User-run Cloudflare setup: `wrangler login`, TURN key, secrets, subdomain, deploy. Then the test matrix: same Wi-Fi, dorm Wi-Fi, PC LAN + phone mobile data, and a forced-relay debug switch.
7. Resilience: ICE restart on network change, credential refresh, and budget enforcement/revocation.

## Open decisions

- PC enrollment method: an owner key pasted once into each PC, or an allowlist edited with `wrangler`.
- When the remote path is validated: keep or retire LAN signaling and the 8765 firewall rule.
