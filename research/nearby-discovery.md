# Finding a nearby PC without the same Wi-Fi

Recorded: 30 October 2026. Status: built and working. Bluetooth discovery plus the relay is now the primary way to connect (user decision, 30 Oct).

**Test (30 Oct):** a Xiaomi phone (Android 14) on `Constructor-Guest` found the PC (on `Constructor`) about 2 seconds after scanning started. With the code, it cast through the relay (PC log: `Cast link: Relayed`). Before the room was added, the same join failed: the phone could not reach the PC's LAN address.

## Short answer

- Find on Wi-Fi uses mDNS (`GocastDiscovery.kt`). It only sees PCs on the same network.
- Bluetooth finds a PC a few metres away, whatever network either device is on.
- The beacon carries the PC's relay room ([remote-connection.md](remote-connection.md)), so the phone joins through the relay from any network.

## What is built

- Home: **Find with Bluetooth** button and list, in the same style as Find on Wi-Fi. Hidden on phones without Bluetooth LE.
- Home: Find on Wi-Fi shows a red count badge when PCs are already found. Wi-Fi search now runs on Home for everyone.
- PC: two Bluetooth beacons while gocast is open, next to the mDNS advert (see below).
- Tapping a PC found over Bluetooth opens the usual Enter code step. With a room, it joins through the relay, LAN as fallback, and the phone saves the room for Last connected.

## 1. The Bluetooth beacon

**How the PC broadcasts.** Windows has a built-in publisher ([`BluetoothLEAdvertisementPublisher`](https://learn.microsoft.com/en-us/uwp/api/windows.devices.bluetooth.advertisement.bluetoothleadvertisementpublisher)). Java and Kotlin cannot call it directly. Three ways in:

| Option | For | Against |
| --- | --- | --- |
| Hidden Windows PowerShell child (**chosen for now**, `ble-beacon.ps1`) | No new build tools. Works on every Windows 10/11. | Some antivirus tools distrust hidden PowerShell. |
| JNA calling WinRT from Kotlin | No child process. Best long term. | About 200 lines of COM plumbing. |
| Small C#/.NET helper exe | Simple code. | Adds .NET to the build and installer. |

Observed on this PC: the MediaTek adapter supports broadcasting, including both beacons at once. While Bluetooth is off, Windows aborts the beacons; the script retries every 3 seconds, so they start by themselves once Bluetooth is on. It exits when gocast exits.

**What gets broadcast** (see `BeaconPayload.kt`). A normal Bluetooth advertisement only leaves 24 bytes, too small for a room id plus a name, so the PC sends two:

- **Short beacon** (every phone): `GC`, version 1, the PC's LAN IPv4 address, the port, the PC name (first 15 bytes).
- **Room beacon** (Bluetooth 5 extended advertisement): version 2, the room id, the address, the port, and the name up to 48 bytes. Needs Windows 10 2004+ and a Bluetooth 5 adapter on the PC, and a phone whose chip hears extended advertisements. Without these, the phone only sees the short beacon and joins over LAN.
- The pairing code is never broadcast. Someone nearby sees the PC name, a private IP and the room id. To join they still need the code on your screen; the PC checks it, as before.
- Company id `0xFFFF` is Bluetooth's "testing" id. Before release: register an id, or switch to a 128-bit service UUID.

**Phone permissions** ([Android docs](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)):

- Android 12+: `BLUETOOTH_SCAN` marked `neverForLocation`. One "Allow Nearby devices?" prompt. No location prompt.
- Android 10–11: needs the location permission, and Location switched on. That prompt is scarier.
- Asked only when you tap Find with Bluetooth.
- With `neverForLocation`, Android hides some known location beacons. Ours passed on Android 14.

**Battery and range.**

- PC: broadcasting costs almost nothing.
- Phone: scans only while the Bluetooth list is on screen. It stops when you leave the list or switch apps.
- Range: about 10 m indoors, through a wall or two. Good for "same room".

## 2. How a found PC connects

- With the room beacon: the phone joins the PC's room through the Cloudflare signaling Worker with the usual code, and WebRTC connects over STUN/TURN (NET-02/03). LAN is the fallback.
- With only the short beacon: the phone joins the LAN address. That works on one network where multicast is blocked, but not across networks or with client isolation.
- After the first join, the phone keeps the room, so Last connected also goes through the relay.

## 3. Alternatives, and why they lose

- **Wi-Fi Direct:** Windows supports it, but it brings pairing prompts on both sides and adapter support varies.
- **Wi-Fi Aware:** Android only. Windows has no support.
- **Google Nearby Connections:** no SDK for Windows apps.
- **Ultrasound:** needs the microphone, is unreliable in noisy rooms, and feels creepy.
- **Accounts:** work anywhere, but break the "no account" goal.
- **QR code:** already works and needs no discovery at all. It stays the fallback.

## 4. What is left

1. Test more phones, including one without Bluetooth 5 extended scanning (it should fall back to LAN).
2. Replace PowerShell with JNA, if antivirus ever complains (about a day).
3. Get a real company id or a 128-bit service UUID before release.

## How to test

1. Turn Bluetooth on, on the PC. Run **Refresh Both.cmd**.
2. On the phone, tap Find with Bluetooth and allow Nearby devices. The PC should appear within a few seconds.
3. Tap it, enter the code, Connect. The PC log should say `Cast link: Relayed` or `Direct`.
