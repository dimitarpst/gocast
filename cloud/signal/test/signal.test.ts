import { runInDurableObject } from "cloudflare:test";
import { env, exports } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import {
	CLOSE_OTHER_PHONE_ACCEPTED,
	CLOSE_PC_OFFLINE,
	CLOSE_PHONE_REPLACED,
	CLOSE_REJECTED,
	CLOSE_REPLACED,
} from "../src/room";
import { acceptedPair, connectRequest, newRoomId, OTHER_KEY, PC_KEY, Peer } from "./helpers";

const STUN_ONLY = [{ urls: ["stun:stun.cloudflare.com:3478"] }];

describe("routing", () => {
	it("answers health checks", async () => {
		const response = await exports.default.fetch("https://signal.test/health");
		expect(response.status).toBe(200);
	});

	it("rejects unknown paths, bad room ids and bad roles", async () => {
		expect((await exports.default.fetch("https://signal.test/")).status).toBe(404);
		expect((await connectRequest("short", "pc", PC_KEY)).status).toBe(404);
		expect((await connectRequest("has spaces in the room id", "pc", PC_KEY)).status).toBe(404);
		expect((await connectRequest(newRoomId(), "viewer", PC_KEY)).status).toBe(400);
	});

	it("requires a WebSocket upgrade", async () => {
		const response = await exports.default.fetch(`https://signal.test/room/${newRoomId()}?role=pc`);
		expect(response.status).toBe(426);
	});
});

describe("room claim", () => {
	it("requires a host key from the PC", async () => {
		expect((await connectRequest(newRoomId(), "pc")).status).toBe(401);
		expect((await connectRequest(newRoomId(), "pc", "too-short")).status).toBe(401);
	});

	it("stores only the SHA-256 of the host key", async () => {
		const roomId = newRoomId();
		const pc = await Peer.connect(roomId, "pc", PC_KEY);
		const stub = env.ROOMS.get(env.ROOMS.idFromName(roomId));
		const stored = await runInDurableObject(stub, (_, state) => [...state.storage.kv.list()]);
		const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(PC_KEY));
		const hex = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
		expect(stored).toEqual([["hostKeyHash", hex]]);
		expect(JSON.stringify(stored)).not.toContain(PC_KEY);
		pc.close();
	});

	it("keeps the room for the PC that claimed it", async () => {
		const roomId = newRoomId();
		const first = await Peer.connect(roomId, "pc", PC_KEY);
		expect((await connectRequest(roomId, "pc", OTHER_KEY)).status).toBe(403);

		// The same PC reconnecting replaces its old socket.
		const second = await Peer.connect(roomId, "pc", PC_KEY);
		expect((await first.closed).code).toBe(CLOSE_REPLACED);
		second.close();
	});
});

describe("relay", () => {
	it("refuses phones while the PC is offline", async () => {
		const response = await connectRequest(newRoomId(), "phone");
		expect(response.status).toBe(503);
	});

	it("relays the existing GoCast messages unchanged", async () => {
		const roomId = newRoomId();
		const pc = await Peer.connect(roomId, "pc", PC_KEY);
		const phone = await Peer.connect(roomId, "phone");
		const joined = await pc.nextJson();
		expect(joined.type).toBe("phone-joined");

		const hello = '{"type":"hello","token":"ABCD","role":"phone"}';
		phone.send(hello);
		expect(await pc.next()).toBe(hello);

		pc.send({ type: "phone-accepted", phoneId: joined.phoneId });
		const ok = '{"trustToken":"trusted","instanceId":"pc-id","displayName":"My PC"}';
		pc.send(ok);
		expect(await phone.next()).toBe(ok);

		const fromPhone = [
			'{"type":"phone-media-ready"}',
			'{"type":"offer","sdp":"v=0\\r\\no=- 1 2 IN IP4 127.0.0.1\\r\\n"}',
			'{"type":"ice","candidate":"candidate:1 1 udp 1 192.168.1.2 5000 typ host","sdpMid":"0","sdpMLineIndex":0}',
			'{"type":"cast-paused"}',
			'{"type":"cast-resumed"}',
		];
		for (const message of fromPhone) {
			phone.send(message);
			expect(await pc.next()).toBe(message);
		}

		const fromPc = [
			'{"type":"viewer-ready"}',
			'{"type":"answer","sdp":"v=0\\r\\n"}',
			'{"type":"ice","candidate":"candidate:2 1 udp 1 10.0.0.2 6000 typ host","sdpMid":"0","sdpMLineIndex":0}',
			'{"type":"session-ended"}',
		];
		for (const message of fromPc) {
			pc.send(message);
			expect(await phone.next()).toBe(message);
		}
		pc.close();
		phone.close();
	});

	it("only lets a phone say hello before the PC accepts it", async () => {
		const roomId = newRoomId();
		const pc = await Peer.connect(roomId, "pc", PC_KEY);
		const phone = await Peer.connect(roomId, "phone");
		await pc.nextJson();

		phone.send('{"type":"offer","sdp":"too early"}');
		pc.send('{"type":"viewer-ready"}');
		expect(await pc.staysQuiet()).toBe(true);
		expect(await phone.staysQuiet()).toBe(true);
		pc.close();
		phone.close();
	});

	it("answers pings without relaying them", async () => {
		const { pc, phone } = await acceptedPair(newRoomId());
		phone.send('{"type":"ping"}');
		expect(await phone.next()).toBe('{"type":"pong"}');
		pc.send('{"type":"ping"}');
		expect(await pc.next()).toBe('{"type":"pong"}');
		expect(await pc.staysQuiet()).toBe(true);
		expect(await phone.staysQuiet()).toBe(true);
		pc.close();
		phone.close();
	});
});

describe("one phone per room", () => {
	it("replaces a waiting phone with a newer one", async () => {
		const roomId = newRoomId();
		const pc = await Peer.connect(roomId, "pc", PC_KEY);
		const first = await Peer.connect(roomId, "phone");
		const firstId = (await pc.nextJson()).phoneId;
		const second = await Peer.connect(roomId, "phone");

		expect((await first.closed).code).toBe(CLOSE_PHONE_REPLACED);
		expect(await pc.nextJson()).toEqual({ type: "phone-left", phoneId: firstId });
		const joined = await pc.nextOfType("phone-joined");
		expect(joined.phoneId).not.toBe(firstId);
		pc.close();
		second.close();
	});

	it("ends the old phone's session when the PC accepts a new one", async () => {
		const roomId = newRoomId();
		const { pc, phone: first, phoneId: firstId } = await acceptedPair(roomId);
		const second = await Peer.connect(roomId, "phone");
		const secondId = (await pc.nextOfType("phone-joined")).phoneId;
		second.send({ type: "hello", token: "trusted" });
		expect((await pc.nextOfType("hello")).token).toBe("trusted");

		// The accepted phone keeps working while the new one only waits.
		first.send('{"type":"cast-paused"}');
		expect(await pc.next()).toBe('{"type":"cast-paused"}');

		pc.send({ type: "phone-accepted", phoneId: secondId });
		expect(await first.next()).toBe('{"type":"session-ended"}');
		expect((await first.closed).code).toBe(CLOSE_OTHER_PHONE_ACCEPTED);
		expect(await pc.nextOfType("phone-left")).toEqual({ type: "phone-left", phoneId: firstId });

		pc.send('{"type":"viewer-ready"}');
		expect(await second.next()).toBe('{"type":"viewer-ready"}');
		pc.close();
		second.close();
	});

	it("closes a phone the PC rejects", async () => {
		const roomId = newRoomId();
		const pc = await Peer.connect(roomId, "pc", PC_KEY);
		const phone = await Peer.connect(roomId, "phone");
		const phoneId = (await pc.nextJson()).phoneId;
		phone.send({ type: "hello", token: "wrong" });
		await pc.next();
		pc.send({ type: "phone-rejected", phoneId });
		expect((await phone.closed).code).toBe(CLOSE_REJECTED);
		expect(await pc.nextOfType("phone-left")).toEqual({ type: "phone-left", phoneId });
		pc.close();
	});

	it("tells the PC when its phone leaves", async () => {
		const { pc, phone, phoneId } = await acceptedPair(newRoomId());
		phone.close();
		expect(await pc.nextOfType("phone-left")).toEqual({ type: "phone-left", phoneId });
		pc.close();
	});

	it("closes phones when the PC goes offline", async () => {
		const { pc, phone } = await acceptedPair(newRoomId());
		pc.close();
		expect((await phone.closed).code).toBe(CLOSE_PC_OFFLINE);
	});
});

describe("ICE servers", () => {
	const relayed = [
		{ urls: ["stun:stun.cloudflare.com:3478"] },
		{
			urls: ["turn:turn.cloudflare.com:3478?transport=udp", "turns:turn.cloudflare.com:443?transport=tcp"],
			username: "test-user",
			credential: "test-credential",
		},
	];

	it("gives relay credentials to the PC of an allowlisted room", async () => {
		const pc = await Peer.connect("allowedRoomAAAAAAAAAAA", "pc", PC_KEY);
		pc.send({ type: "get-ice-servers" });
		expect(await pc.nextJson()).toEqual({ type: "ice-servers", iceServers: relayed });
		pc.close();
	});

	it("gives STUN only to the PC of any other room", async () => {
		const pc = await Peer.connect(newRoomId(), "pc", PC_KEY);
		pc.send({ type: "get-ice-servers" });
		expect(await pc.nextJson()).toEqual({ type: "ice-servers", iceServers: STUN_ONLY });
		pc.close();
	});

	it("gives relay credentials only to the phone the PC accepted", async () => {
		const roomId = "allowedRoomBBBBBBBBBBB";
		const pc = await Peer.connect(roomId, "pc", PC_KEY);
		const phone = await Peer.connect(roomId, "phone");
		const phoneId = (await pc.nextJson()).phoneId;

		phone.send({ type: "get-ice-servers" });
		expect(await phone.nextJson()).toEqual({ type: "ice-servers", iceServers: STUN_ONLY });

		phone.send({ type: "hello", token: "ABCD" });
		await pc.next();
		pc.send({ type: "phone-accepted", phoneId });
		phone.send({ type: "get-ice-servers" });
		expect(await phone.nextJson()).toEqual({ type: "ice-servers", iceServers: relayed });
		expect(await pc.staysQuiet()).toBe(true);
		pc.close();
		phone.close();
	});

	it("gives STUN only to an accepted phone in a room that is not allowlisted", async () => {
		const { pc, phone } = await acceptedPair(newRoomId());
		phone.send({ type: "get-ice-servers" });
		expect(await phone.nextJson()).toEqual({ type: "ice-servers", iceServers: STUN_ONLY });
		pc.close();
		phone.close();
	});
});
