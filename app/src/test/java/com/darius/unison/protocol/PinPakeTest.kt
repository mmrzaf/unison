package com.darius.unison.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPakeTest {
    @Test
    fun matchingCodeProducesMutuallyVerifiedSessionKey() {
        val client = PinPake.ClientSession.start("room", "peer", "client-nonce", "1234")
        val server =
            PinPake.ServerSession.start(
                "room",
                "peer",
                "client-nonce",
                "1234",
                client.publicValueBase64,
            )
        val answer = client.answer(server.challenge)
        val accepted = checkNotNull(server.verify(answer.proofBase64))

        assertTrue(answer.sessionKey.contentEquals(accepted.sessionKey))
        assertTrue(
            PinPake.verifyServerProof(answer.expectedServerProofBase64, accepted.proofBase64)
        )
        answer.sessionKey.fill(0)
        accepted.sessionKey.fill(0)
    }

    @Test
    fun wrongCodeCannotAuthenticate() {
        val client = PinPake.ClientSession.start("room", "peer", "client-nonce", "9999")
        val server =
            PinPake.ServerSession.start(
                "room",
                "peer",
                "client-nonce",
                "1234",
                client.publicValueBase64,
            )
        val answer = client.answer(server.challenge)
        assertTrue(server.verify(answer.proofBase64) == null)
        answer.sessionKey.fill(0)
    }

    @Test
    fun proofAndSessionCannotBeReplayed() {
        val client = PinPake.ClientSession.start("room", "peer", "client-nonce", "1234")
        val server =
            PinPake.ServerSession.start(
                "room",
                "peer",
                "client-nonce",
                "1234",
                client.publicValueBase64,
            )
        val answer = client.answer(server.challenge)
        assertTrue(server.verify(answer.proofBase64) != null)
        assertTrue(server.verify(answer.proofBase64) == null)
        assertTrue(runCatching { client.answer(server.challenge) }.isFailure)
        answer.sessionKey.fill(0)
    }

    @Test
    fun publicValuesDoNotContainTheRoomCode() {
        val client = PinPake.ClientSession.start("room", "peer", "client-nonce", "0427")
        val server =
            PinPake.ServerSession.start(
                "room",
                "peer",
                "client-nonce",
                "0427",
                client.publicValueBase64,
            )
        assertFalse(client.publicValueBase64.contains("0427"))
        assertFalse(server.challenge.serverPublicValueBase64.contains("0427"))
        assertFalse(server.challenge.saltBase64.contains("0427"))
    }
}
