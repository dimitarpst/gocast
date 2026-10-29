import { describe, expect, it } from "vitest";
import { newRoomId, PC_KEY, Peer, acceptedPair } from "./helpers";

// RELAY_PASSWORD is "test-relay-password" in vitest.config.ts.
const PASSWORD = "test-relay-password";
const STUN_ONLY = [{ urls: ["stun:stun.cloudflare.com:3478"] }];

async function relayStatus(pc: Peer): Promise<Record<string, unknown>> {
	pc.send({ type: "get-relay-status" });
	return pc.nextOfType("relay-status");
}

async function unlock(pc: Peer, password: unknown): Promise<Record<string, unknown>> {
	pc.send({ type: "relay-unlock", password });
	return pc.nextOfType("relay-status");
}

async function hasTurn(peer: Peer): Promise<boolean> {
	peer.send({ type: "get-ice-servers" });
	const reply = await peer.nextOfType("ice-servers");
	return (reply.iceServers as { urls: string[] }[]).some((s) => s.urls.some((u) => u.startsWith("turn")));
}

describe("relay password", () => {
	it("keeps a new room locked to STUN only", async () => {
		const pc = await Peer.connect(newRoomId(), "pc", PC_KEY);
		expect(await relayStatus(pc)).toEqual({ type: "relay-status", relay: "locked" });
		pc.send({ type: "get-ice-servers" });
		expect(await pc.nextJson()).toEqual({ type: "ice-servers", iceServers: STUN_ONLY });
		pc.close();
	});

	it("rejects a wrong password", async () => {
		const pc = await Peer.connect(newRoomId(), "pc", PC_KEY);
		expect(await unlock(pc, "nope")).toEqual({ type: "relay-status", relay: "locked", error: "wrong-password" });
		expect(await unlock(pc, 42)).toEqual({ type: "relay-status", relay: "locked", error: "wrong-password" });
		expect(await hasTurn(pc)).toBe(false);
		pc.close();
	});

	it("unlocks the room for the PC and its accepted phone, and remembers it", async () => {
		const roomId = newRoomId();
		const { pc, phone } = await acceptedPair(roomId);
		expect(await unlock(pc, ` ${PASSWORD} `)).toEqual({ type: "relay-status", relay: "on" });
		expect(await hasTurn(pc)).toBe(true);
		expect(await hasTurn(phone)).toBe(true);
		pc.close();
		phone.close();

		const again = await Peer.connect(roomId, "pc", PC_KEY);
		expect(await relayStatus(again)).toEqual({ type: "relay-status", relay: "on" });
		again.close();
	});

	it("unlocking one room does not unlock another", async () => {
		const first = await Peer.connect(newRoomId(), "pc", PC_KEY);
		await unlock(first, PASSWORD);
		const second = await Peer.connect(newRoomId(), "pc", PC_KEY);
		expect(await relayStatus(second)).toEqual({ type: "relay-status", relay: "locked" });
		first.close();
		second.close();
	});

	it("stops accepting attempts after five wrong passwords", async () => {
		const pc = await Peer.connect(newRoomId(), "pc", PC_KEY);
		for (let i = 0; i < 5; i++) await unlock(pc, `wrong-${i}`);
		expect(await unlock(pc, PASSWORD)).toEqual({
			type: "relay-status",
			relay: "locked",
			error: "too-many-attempts",
		});
		expect(await hasTurn(pc)).toBe(false);
		pc.close();
	});

	it("reports an allowlisted room as on without a password", async () => {
		const pc = await Peer.connect("allowedRoomAAAAAAAAAAA", "pc", PC_KEY);
		expect(await relayStatus(pc)).toEqual({ type: "relay-status", relay: "on" });
		pc.close();
	});
});
