package com.darius.unison.protocol

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Test

/** RFC 5054 Appendix B SRP-6a arithmetic conformance vector (SHA-1, 1024-bit group). */
class Srp6aCoreRfc5054Test {
    @Test
    fun appendixBVerifierPublicValuesScramblingAndPremasterSecretMatch() {
        val modulus =
            hex(
                "EEAF0AB9ADB38DD69C33F80AFA8FC5E86072618775FF3C0B9EA2314C" +
                    "9C256576D674DF7496EA81D3383B4813D692C6E0E0D5D8E250B98BE4" +
                    "8E495C1D6089DAD15DC7D7B46154D6B6CE8EF4AD69B15D4982559B29" +
                    "7BCF1885C529F566660E57EC68EDBC3C05726CC02FD4CBF4976EAA9A" +
                    "FD5138FE8376435B9FC61D2FC0EB06E3"
            )
        val generator = BigInteger.valueOf(2L)
        val salt = bytes("BEB25379D1A8581EB5A727673A2441EE")
        val clientPrivate = hex("60975527035CF2AD1989806F0407210BC81EDC04E2762A56AFD529DDDA2D4393")
        val serverPrivate = hex("E487CB59D31AC550471E81F00F6928E01DDA08E974A004F49E61F5D105284D20")

        val multiplier = Srp6aCore.multiplier(modulus, generator, "SHA-1")
        val privateKey = Srp6aCore.privateKey("alice", "password123", salt.copyOf(), "SHA-1")
        val verifier = Srp6aCore.verifier(modulus, generator, privateKey)
        val clientPublic = Srp6aCore.clientPublicValue(modulus, generator, clientPrivate)
        val serverPublic =
            Srp6aCore.serverPublicValue(
                modulus,
                generator,
                multiplier,
                verifier,
                serverPrivate,
            )
        val scrambling = Srp6aCore.scramblingParameter(modulus, clientPublic, serverPublic, "SHA-1")
        val clientSecret =
            Srp6aCore.clientSharedSecret(
                modulus = modulus,
                generator = generator,
                multiplier = multiplier,
                privateKey = privateKey,
                clientPrivateValue = clientPrivate,
                serverPublicValue = serverPublic,
                scramblingParameter = scrambling,
            )
        val serverSecret =
            Srp6aCore.serverSharedSecret(
                modulus = modulus,
                verifier = verifier,
                clientPublicValue = clientPublic,
                serverPrivateValue = serverPrivate,
                scramblingParameter = scrambling,
            )

        assertEquals(hex("7556AA045AEF2CDD07ABAF0F665C3E818913186F"), multiplier)
        assertEquals(hex("94B7555AABE9127CC58CCF4993DB6CF84D16C124"), privateKey)
        assertEquals(
            hex(
                "7E273DE8696FFC4F4E337D05B4B375BEB0DDE1569E8FA00A9886D812" +
                    "9BADA1F1822223CA1A605B530E379BA4729FDC59F105B4787E5186F5" +
                    "C671085A1447B52A48CF1970B4FB6F8400BBF4CEBFBB168152E08AB5" +
                    "EA53D15C1AFF87B2B9DA6E04E058AD51CC72BFC9033B564E26480D78" +
                    "E955A5E29E7AB245DB2BE315E2099AFB"
            ),
            verifier,
        )
        assertEquals(
            hex(
                "61D5E490F6F1B79547B0704C436F523DD0E560F0C64115BB72557EC4" +
                    "4352E8903211C04692272D8B2D1A5358A2CF1B6E0BFCF99F921530EC" +
                    "8E39356179EAE45E42BA92AEACED825171E1E8B9AF6D9C03E1327F44" +
                    "BE087EF06530E69F66615261EEF54073CA11CF5858F0EDFDFE15EFEA" +
                    "B349EF5D76988A3672FAC47B0769447B"
            ),
            clientPublic,
        )
        assertEquals(
            hex(
                "BD0C61512C692C0CB6D041FA01BB152D4916A1E77AF46AE105393011" +
                    "BAF38964DC46A0670DD125B95A981652236F99D9B681CBF87837EC99" +
                    "6C6DA04453728610D0C6DDB58B318885D7D82C7F8DEB75CE7BD4FBAA" +
                    "37089E6F9C6059F388838E7A00030B331EB76840910440B1B27AAEAE" +
                    "EB4012B7D7665238A8E3FB004B117B58"
            ),
            serverPublic,
        )
        assertEquals(hex("CE38B9593487DA98554ED47D70A7AE5F462EF019"), scrambling)
        val expectedSecret =
            hex(
                "B0DC82BABCF30674AE450C0287745E7990A3381F63B387AAF271A10D" +
                    "233861E359B48220F7C4693C9AE12B0A6F67809F0876E2D013800D6C" +
                    "41BB59B6D5979B5C00A172B4A2A5903A0BDCAF8A709585EB2AFAFA8F" +
                    "3499B200210DCC1F10EB33943CD67FC88A2F39A4BE5BEC4EC0A3212D" +
                    "C346D7E474B29EDE8A469FFECA686E5A"
            )
        assertEquals(expectedSecret, clientSecret)
        assertEquals(expectedSecret, serverSecret)
    }

    private fun hex(value: String): BigInteger = BigInteger(value, 16)

    private fun bytes(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
