package org.yarokovisty.vpnis.data.vpn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Unit tests for [XrayConfigBuilder].
 *
 * Tests parse the returned JSON with the kotlinx.serialization element API so they are
 * independent of the internal assembly implementation. [android.util.Log] stubs are
 * silenced via `testOptions { unitTests { isReturnDefaultValues = true } }`.
 */
class XrayConfigBuilderTest {

    // -------------------------------------------------------------------------
    // Fixture
    // -------------------------------------------------------------------------

    private val uuid = "12345678-1234-1234-1234-123456789abc"
    private val host = "vpn.example.com"
    private val port = 443
    private val pbk = "abcdefghijklmnopqrstuvwxyz012345"
    private val sid = "deadbeef"
    private val sni = "www.google.com"
    private val fp = "chrome"

    /** A well-formed VLESS/Reality URI that covers every required field. */
    private val validUri =
        "vless://$uuid@$host:$port" +
            "?type=tcp&security=reality&pbk=$pbk&fp=$fp&sni=$sni&sid=$sid" +
            "#MyLabel"

    // -------------------------------------------------------------------------
    // Happy path — SOCKS5 inbound
    // -------------------------------------------------------------------------

    @Test
    fun `valid VLESS URI EXPECT inbound protocol is socks`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val protocol = root["inbounds"]!!.jsonArray[0].jsonObject["protocol"]!!.jsonPrimitive.content
        assertEquals("socks", protocol)
    }

    @Test
    fun `valid VLESS URI EXPECT inbound listen is 127_0_0_1`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val listen = root["inbounds"]!!.jsonArray[0].jsonObject["listen"]!!.jsonPrimitive.content
        assertEquals("127.0.0.1", listen)
    }

    @Test
    fun `valid VLESS URI EXPECT inbound port equals TunConfig localSocksPort`() {
        // Given
        val expectedPort = TunConfig().localSocksPort

        // When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val actualPort = root["inbounds"]!!.jsonArray[0].jsonObject["port"]!!.jsonPrimitive.int
        assertEquals(expectedPort, actualPort)
    }

    // -------------------------------------------------------------------------
    // Happy path — VLESS outbound host / port
    // -------------------------------------------------------------------------

    @Test
    fun `valid VLESS URI EXPECT outbound protocol is vless`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val protocol = root["outbounds"]!!.jsonArray[0].jsonObject["protocol"]!!.jsonPrimitive.content
        assertEquals("vless", protocol)
    }

    @Test
    fun `valid VLESS URI EXPECT outbound address equals URI host`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val address = root["outbounds"]!!.jsonArray[0]
            .jsonObject["settings"]!!.jsonObject["vnext"]!!.jsonArray[0]
            .jsonObject["address"]!!.jsonPrimitive.content
        assertEquals(host, address)
    }

    @Test
    fun `valid VLESS URI EXPECT outbound port equals URI port`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val actualPort = root["outbounds"]!!.jsonArray[0]
            .jsonObject["settings"]!!.jsonObject["vnext"]!!.jsonArray[0]
            .jsonObject["port"]!!.jsonPrimitive.int
        assertEquals(port, actualPort)
    }

    // -------------------------------------------------------------------------
    // Happy path — VLESS outbound user id
    // -------------------------------------------------------------------------

    @Test
    fun `valid VLESS URI EXPECT outbound user id equals UUID`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val userId = root["outbounds"]!!.jsonArray[0]
            .jsonObject["settings"]!!.jsonObject["vnext"]!!.jsonArray[0]
            .jsonObject["users"]!!.jsonArray[0]
            .jsonObject["id"]!!.jsonPrimitive.content
        assertEquals(uuid, userId)
    }

    // -------------------------------------------------------------------------
    // Happy path — streamSettings network
    // -------------------------------------------------------------------------

    @Test
    fun `valid VLESS URI EXPECT streamSettings network is tcp`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val network = root["outbounds"]!!.jsonArray[0]
            .jsonObject["streamSettings"]!!.jsonObject["network"]!!.jsonPrimitive.content
        assertEquals("tcp", network)
    }

    // -------------------------------------------------------------------------
    // Happy path — realitySettings fields
    // -------------------------------------------------------------------------

    @Test
    fun `valid VLESS URI EXPECT realitySettings publicKey equals pbk param`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val publicKey = root["outbounds"]!!.jsonArray[0]
            .jsonObject["streamSettings"]!!.jsonObject["realitySettings"]!!
            .jsonObject["publicKey"]!!.jsonPrimitive.content
        assertEquals(pbk, publicKey)
    }

    @Test
    fun `valid VLESS URI EXPECT realitySettings shortId equals sid param`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val shortId = root["outbounds"]!!.jsonArray[0]
            .jsonObject["streamSettings"]!!.jsonObject["realitySettings"]!!
            .jsonObject["shortId"]!!.jsonPrimitive.content
        assertEquals(sid, shortId)
    }

    @Test
    fun `valid VLESS URI EXPECT realitySettings serverName equals sni param`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val serverName = root["outbounds"]!!.jsonArray[0]
            .jsonObject["streamSettings"]!!.jsonObject["realitySettings"]!!
            .jsonObject["serverName"]!!.jsonPrimitive.content
        assertEquals(sni, serverName)
    }

    @Test
    fun `valid VLESS URI EXPECT realitySettings fingerprint equals fp param`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val fingerprint = root["outbounds"]!!.jsonArray[0]
            .jsonObject["streamSettings"]!!.jsonObject["realitySettings"]!!
            .jsonObject["fingerprint"]!!.jsonPrimitive.content
        assertEquals(fp, fingerprint)
    }

    // -------------------------------------------------------------------------
    // DNS-storm fix (issue #111) — sniffing / dns / routing / dns-out
    // -------------------------------------------------------------------------

    @Test
    fun `valid VLESS URI EXPECT socks inbound has sniffing with tls and quic destOverride`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val sniffing = root["inbounds"]!!.jsonArray[0].jsonObject["sniffing"]!!.jsonObject
        assertEquals("true", sniffing["enabled"]!!.jsonPrimitive.content)
        val destOverride = sniffing["destOverride"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("http", "tls", "quic"), destOverride)
    }

    @Test
    fun `valid VLESS URI EXPECT dns object has servers and IPv4 query strategy`() {
        // Given
        val expectedServers = TunConfig().dnsServers

        // When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val dns = root["dns"]!!.jsonObject
        val servers = dns["servers"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(expectedServers, servers)
        assertEquals("UseIPv4", dns["queryStrategy"]!!.jsonPrimitive.content)
    }

    @Test
    fun `valid VLESS URI EXPECT a dns-out outbound with protocol dns exists`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val dnsOut = root["outbounds"]!!.jsonArray
            .map { it.jsonObject }
            .single { it["tag"]?.jsonPrimitive?.content == "dns-out" }
        assertEquals("dns", dnsOut["protocol"]!!.jsonPrimitive.content)
    }

    @Test
    fun `valid VLESS URI EXPECT routing sends port 53 to dns-out (never leaks DNS)`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then — a field rule routes DNS (port 53) to the dns-out handler, not direct/proxy.
        val rule = root["routing"]!!.jsonObject["rules"]!!.jsonArray
            .map { it.jsonObject }
            .single { it["port"]?.jsonPrimitive?.content == "53" }
        assertEquals("dns-out", rule["outboundTag"]!!.jsonPrimitive.content)
    }

    @Test
    fun `valid VLESS URI EXPECT socks inbound is tagged userLevel 8`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then — userLevel 8 makes the level-8 policy apply to this inbound's traffic.
        val settings = root["inbounds"]!!.jsonArray[0].jsonObject["settings"]!!.jsonObject
        assertEquals(8, settings["userLevel"]!!.jsonPrimitive.int)
    }

    @Test
    fun `valid VLESS URI EXPECT level 8 policy reaps half-closed connections aggressively`() {
        // Given / When
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then — uplinkOnly/downlinkOnly = 1 keep the concurrent connection count low (issue #111).
        val level8 = root["policy"]!!.jsonObject["levels"]!!.jsonObject["8"]!!.jsonObject
        assertEquals(1, level8["uplinkOnly"]!!.jsonPrimitive.int)
        assertEquals(1, level8["downlinkOnly"]!!.jsonPrimitive.int)
    }

    @Test
    fun `valid VLESS URI EXPECT proxy-out is the first outbound (default route)`() {
        // Given / When — order matters: Xray routes unmatched traffic to the FIRST outbound,
        // so proxy-out must stay first or all traffic would fall through to dns-out/direct.
        val json = requireNotNull(XrayConfigBuilder.build(validUri))
        val root = Json.parseToJsonElement(json).jsonObject

        // Then
        val firstTag = root["outbounds"]!!.jsonArray[0].jsonObject["tag"]!!.jsonPrimitive.content
        assertEquals("proxy-out", firstTag)
    }

    // -------------------------------------------------------------------------
    // Injection-safety — special characters in sni are properly escaped
    // -------------------------------------------------------------------------

    /**
     * A URI whose `sni` value contains `"`, `}`, and `\`. These characters would break
     * a string-interpolated JSON. The builder uses kotlinx.serialization element API so
     * they must be escaped and the result must still be parseable JSON with the raw value
     * preserved inside the string node.
     */
    @Test
    fun `sni containing double-quote and brace EXPECT returned JSON is well-formed`() {
        // Given — percent-encode the dangerous chars so java.net.URI parses the query correctly
        val dangerousSni = "x%22y%7Dz" // raw: x"y}z
        val uri = "vless://$uuid@$host:$port" +
            "?type=tcp&security=reality&pbk=$pbk&fp=$fp&sni=$dangerousSni&sid=$sid"

        // When
        val json = XrayConfigBuilder.build(uri)

        // Then — must be parseable (not null, not malformed JSON)
        assertNotNull(json)
        val root = Json.parseToJsonElement(json!!).jsonObject // throws on malformed JSON
        val serverName = root["outbounds"]!!.jsonArray[0]
            .jsonObject["streamSettings"]!!.jsonObject["realitySettings"]!!
            .jsonObject["serverName"]!!.jsonPrimitive.content
        // The raw (decoded) value must be preserved exactly
        assertEquals("x\"y}z", serverName)
    }

    @Test
    fun `sni containing backslash EXPECT returned JSON is well-formed and value is preserved`() {
        // Given — percent-encode backslash so java.net.URI accepts the query string
        val dangerousSni = "a%5Cb" // raw: a\b
        val uri = "vless://$uuid@$host:$port" +
            "?type=tcp&security=reality&pbk=$pbk&fp=$fp&sni=$dangerousSni&sid=$sid"

        // When
        val json = XrayConfigBuilder.build(uri)

        // Then
        assertNotNull(json)
        val root = Json.parseToJsonElement(json!!).jsonObject
        val serverName = root["outbounds"]!!.jsonArray[0]
            .jsonObject["streamSettings"]!!.jsonObject["realitySettings"]!!
            .jsonObject["serverName"]!!.jsonPrimitive.content
        assertEquals("a\\b", serverName)
    }

    // -------------------------------------------------------------------------
    // Malformed / unsupported input → null (parameterized)
    // -------------------------------------------------------------------------
    // Covered by the dedicated parameterized class below.
}

/**
 * Parameterized test that asserts `XrayConfigBuilder.build` returns `null` for every
 * unsupported or malformed URI. Each row is a distinct rejection reason.
 */
@RunWith(Parameterized::class)
class XrayConfigBuilderMalformedUriTest(private val input: String, private val label: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{1}")
        fun malformedCases(): List<Array<Any>> = listOf(
            arrayOf("not a uri", "plain string"),
            arrayOf("", "empty string"),
            arrayOf("http://example.com:443", "http scheme not vless"),
            arrayOf(
                "vless://@example.com:443?type=tcp&security=reality" +
                    "&pbk=k&fp=chrome&sni=x.com&sid=ab",
                "missing uuid",
            ),
            arrayOf(
                "vless://uuid-here@:443?type=tcp&security=reality" +
                    "&pbk=k&fp=chrome&sni=x.com&sid=ab",
                "missing host",
            ),
            arrayOf(
                "vless://uuid-here@example.com?type=tcp&security=reality" +
                    "&pbk=k&fp=chrome&sni=x.com&sid=ab",
                "missing port",
            ),
            arrayOf(
                "vless://uuid-here@example.com:443?type=udp&security=reality" +
                    "&pbk=k&fp=chrome&sni=x.com&sid=ab",
                "unsupported type udp",
            ),
            arrayOf(
                "vless://uuid-here@example.com:443?type=tcp&security=tls" +
                    "&pbk=k&fp=chrome&sni=x.com&sid=ab",
                "unsupported security tls",
            ),
            arrayOf(
                "vless://uuid-here@example.com:443?type=tcp&security=reality" +
                    "&fp=chrome&sni=x.com&sid=ab",
                "missing pbk",
            ),
            arrayOf(
                "vless://uuid-here@example.com:443?type=tcp&security=reality" +
                    "&pbk=k&sni=x.com&sid=ab",
                "missing fp",
            ),
            arrayOf(
                "vless://uuid-here@example.com:443?type=tcp&security=reality" +
                    "&pbk=k&fp=chrome&sid=ab",
                "missing sni",
            ),
            arrayOf(
                "vless://uuid-here@example.com:443?type=tcp&security=reality" +
                    "&pbk=k&fp=chrome&sni=x.com",
                "missing sid",
            ),
        )
    }

    @Test
    fun `malformed or unsupported URI EXPECT build returns null`() {
        // Given — input injected via @Parameterized.Parameters

        // When
        val result = XrayConfigBuilder.build(input)

        // Then
        assertNull("Expected null for input '$label' but got: $result", result)
    }
}
