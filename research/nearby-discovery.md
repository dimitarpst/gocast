# Finding a nearby PC without the same Wi-Fi

Recorded: 30 October 2026. Status: Bluetooth discovery built in source. Not tested on a phone. Connection code is unchanged.

## Short answer

- Find on Wi-Fi uses mDNS (`GocastDiscovery.kt`). It only sees PCs on the same network. It fails on Wi-Fi with client isolation (NET-01).
- Bluetooth can find a PC a few metres away, whatever network either device is on.
- Bluetooth only *finds* the PC. The video still goes over the network. Across networks, that needs the room/relay plan in [remote-connection.md](remote-connection.md) (NET-02/03), which is not deployed.

## What is built now (source only)

- Home: Find on Wi-Fi shows a red count badge when PCs are already found. Wi-Fi search now runs on Home for everyone, not only after a first pairing.
- Home: a new **Find with Bluetooth** button and list. It is hidden on phones without Bluetooth LE.
- PC: broadcasts a Bluetooth beacon while gocast is open, next to the existing mDNS advert.
- Tapping a PC found over Bluetooth opens the usual Enter code step and joins its LAN address. Same join code path as Wi-Fi.

## 1. The Bluetooth beacon

**How the PC broadcasts.** Windows has a built-in publisher ([`BluetoothLEAdvertisementPublisher`](https://learn.microsoft.com/en-us/uwp/api/windows.devices.bluetooth.advertisement.bluetoothleadvertisementpublisher)). Java and Kotlin cannot call it directly. Three ways in:

| Option | For | Against |
| --- | --- | --- |
| Hidden Windows PowerShell child (**chosen for now**, `ble-beacon.ps1`) | No new build tools. Works on every Windows 10/11. | Some antivirus tools distrust hidden PowerShell. |
| JNA calling WinRT from Kotlin | No child process. Best long term. | About 200 lines of COM plumbing. |
| Small C#/.NET helper exe | Simple code. | Adds .NET to the build and installer. |

Observed on this PC: the MediaTek adapter supports broadcasting. Bluetooth was switched off, so Windows aborted the test beacon. The script retries every 3 seconds, so the beacon starts by itself once Bluetooth is on. It exits when gocast exits.

**What gets broadcast** (24 bytes, see `BeaconPayload.kt`): `GC`, a version byte, the PC's LAN IPv4 address, the port, and the PC name (first 15 bytes).

- The pairing code is never broadcast. Someone nearby sees the PC name and a private IP, nothing more. They still need the code on your screen.
- Company id `0xFFFF` is Bluetooth's "testing" id. Before release: register an id, or switch to a 128-bit service UUID.

**Phone permissions** ([Android docs](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)):

- Android 12+: `BLUETOOTH_SCAN` marked `neverForLocation`. One "Allow Nearby devices?" prompt. No location prompt.
- Android 10–11: needs the location permission, and Location switched on. That prompt is scarier.
- Asked only when you tap Find with Bluetooth.
- Risk: with `neverForLocation`, Android hides some known location beacons. Our custom data should pass. Check on a real phone.

**Battery and range.**

- PC: broadcasting costs almost nothing.
- Phone: scans only while the Bluetooth list is on screen. It stops when you leave the list or switch apps.
- Range: about 10 m indoors, through a wall or two. Good for "same room".

## 2. How a found PC connects

- Today the beacon carries the PC's LAN address. The phone uses the normal pairing code flow.
- That works when both are on one network but multicast is blocked (some routers do this).
- It does not work with client isolation or different networks. You then get today's "can't reach PC" error.
- Next step: add the room id to the beacon (8 bytes fit). The phone joins the room through the Cloudflare signaling Worker, and WebRTC connects over STUN/TURN. That is NET-02/03, and it needs the NET-01 talk first.

## 3. Alternatives, and why they lose

- **Wi-Fi Direct:** Windows supports it, but it brings pairing prompts on both sides and adapter support varies.
- **Wi-Fi Aware:** Android only. Windows has no support.
- **Google Nearby Connections:** no SDK for Windows apps.
- **Ultrasound:** needs the microphone, is unreliable in noisy rooms, and feels creepy.
- **Accounts:** work anywhere, but break the "no account" goal.
- **QR code:** already works and needs no discovery at all. It stays the fallback.

## 4. Recommendation and size

1. Keep Bluetooth as built. About a day of real-device testing and fixes.
2. Talk through NET-01, then build the relay (NET-02/03, several days). Bluetooth plus a room id gives "open the app, tap the PC, enter the code" from anywhere nearby.
3. Later: replace PowerShell with JNA (about a day), and get a real company id or UUID.

## How to test

1. Turn Bluetooth on, on the PC. Run **Refresh Both.cmd**.
2. On the phone, tap Find with Bluetooth and allow Nearby devices. The PC should appear within a few seconds.
3. Leave the list, then come back: scanning should stop and start again.
