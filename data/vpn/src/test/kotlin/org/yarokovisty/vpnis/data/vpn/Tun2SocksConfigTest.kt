package org.yarokovisty.vpnis.data.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Tun2SocksConfigTest {

    // -------------------------------------------------------------------------
    // toYaml — full-string equality for the all-defaults case
    // -------------------------------------------------------------------------

    @Test
    fun `toYaml with only required socksPort EXPECT exact default YAML`() {
        // Given
        val config = Tun2SocksConfig(socksPort = 10808)

        // When
        val yaml = config.toYaml()

        // Then
        val expected =
            "tunnel:\n" +
                "  mtu: 1500\n" +
                "  ipv4: 10.0.0.2\n" +
                "socks5:\n" +
                "  address: 127.0.0.1\n" +
                "  port: 10808\n" +
                "  udp: 'udp'\n" +
                "misc:\n" +
                "  tcp-read-write-timeout: 300000\n" +
                "  udp-read-write-timeout: 60000\n" +
                "  log-level: warn\n"
        assertEquals(expected, yaml)
    }

    // -------------------------------------------------------------------------
    // toYaml — trailing newline
    // -------------------------------------------------------------------------

    @Test
    fun `toYaml EXPECT output ends with trailing newline`() {
        // Given
        val config = Tun2SocksConfig(socksPort = 10808)

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.endsWith("\n"))
    }

    // -------------------------------------------------------------------------
    // toYaml — tunnel addresses (v2rayNG parity, issue #111)
    // -------------------------------------------------------------------------

    @Test
    fun `toYaml with ipv4Address EXPECT tunnel ipv4 line`() {
        // Given
        val config = Tun2SocksConfig(socksPort = 10808, ipv4Address = "10.1.2.3")

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.contains("  ipv4: 10.1.2.3\n"))
    }

    @Test
    fun `toYaml without ipv6Address EXPECT no ipv6 line`() {
        // Given — ipv6Address defaults to null
        val config = Tun2SocksConfig(socksPort = 10808)

        // When
        val yaml = config.toYaml()

        // Then
        assertFalse(yaml.contains("ipv6"))
    }

    @Test
    fun `toYaml with ipv6Address EXPECT single-quoted ipv6 line`() {
        // Given
        val config = Tun2SocksConfig(socksPort = 10808, ipv6Address = "fd00::1")

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.contains("  ipv6: 'fd00::1'\n"))
    }

    // -------------------------------------------------------------------------
    // toYaml — single-quoted udp value
    // -------------------------------------------------------------------------

    @Test
    fun `toYaml default udpMode EXPECT udp value is single-quoted`() {
        // Given
        val config = Tun2SocksConfig(socksPort = 10808)

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.contains("  udp: 'udp'"))
    }

    @Test
    fun `toYaml with udpMode tcp EXPECT tcp value is single-quoted`() {
        // Given
        val config = Tun2SocksConfig(socksPort = 10808, udpMode = "tcp")

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.contains("  udp: 'tcp'"))
    }

    // -------------------------------------------------------------------------
    // toYaml — overridden fields propagate
    // -------------------------------------------------------------------------

    @Test
    fun `toYaml with custom socksAddress EXPECT address line reflects override`() {
        // Given
        val config = Tun2SocksConfig(socksAddress = "10.0.0.1", socksPort = 1080)

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.contains("  address: 10.0.0.1\n"))
    }

    @Test
    fun `toYaml with custom socksPort EXPECT port line reflects override`() {
        // Given
        val config = Tun2SocksConfig(socksPort = 1234)

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.contains("  port: 1234\n"))
    }

    @Test
    fun `toYaml with custom mtu EXPECT mtu line reflects override`() {
        // Given
        val config = Tun2SocksConfig(socksPort = 10808, mtu = 1400)

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.contains("  mtu: 1400\n"))
    }

    @Test
    fun `toYaml with custom timeouts EXPECT timeout lines reflect override`() {
        // Given
        val config = Tun2SocksConfig(socksPort = 10808, tcpTimeoutMs = 120_000, udpTimeoutMs = 30_000)

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.contains("  tcp-read-write-timeout: 120000\n"))
        assertTrue(yaml.contains("  udp-read-write-timeout: 30000\n"))
    }

    @Test
    fun `toYaml with custom logLevel EXPECT log-level line reflects override`() {
        // Given
        val config = Tun2SocksConfig(socksPort = 10808, logLevel = "debug")

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.contains("  log-level: debug\n"))
    }

    @Test
    fun `toYaml with all fields overridden EXPECT exact full YAML`() {
        // Given
        val config = Tun2SocksConfig(
            socksAddress = "192.168.1.1",
            socksPort = 9090,
            ipv4Address = "10.9.9.2",
            ipv6Address = "fd11::2",
            mtu = 1400,
            udpMode = "tcp",
            tcpTimeoutMs = 120_000,
            udpTimeoutMs = 30_000,
            logLevel = "error",
        )

        // When
        val yaml = config.toYaml()

        // Then
        val expected =
            "tunnel:\n" +
                "  mtu: 1400\n" +
                "  ipv4: 10.9.9.2\n" +
                "  ipv6: 'fd11::2'\n" +
                "socks5:\n" +
                "  address: 192.168.1.1\n" +
                "  port: 9090\n" +
                "  udp: 'tcp'\n" +
                "misc:\n" +
                "  tcp-read-write-timeout: 120000\n" +
                "  udp-read-write-timeout: 30000\n" +
                "  log-level: error\n"
        assertEquals(expected, yaml)
    }

    // -------------------------------------------------------------------------
    // Validation — socksPort boundaries
    // -------------------------------------------------------------------------

    @Test
    fun `construct with socksPort 1 EXPECT no exception`() {
        // Given / When / Then — construction must not throw
        Tun2SocksConfig(socksPort = 1)
    }

    @Test
    fun `construct with socksPort 65535 EXPECT no exception`() {
        // Given / When / Then — construction must not throw
        Tun2SocksConfig(socksPort = 65535)
    }

    @Test
    fun `construct with socksPort 0 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            Tun2SocksConfig(socksPort = 0)
        }
    }

    @Test
    fun `construct with socksPort 65536 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            Tun2SocksConfig(socksPort = 65536)
        }
    }

    // -------------------------------------------------------------------------
    // Validation — socksPort error message
    // -------------------------------------------------------------------------

    @Test
    fun `construct with socksPort 0 EXPECT error message mentions got 0`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Tun2SocksConfig(socksPort = 0)
        }

        // Then
        assertTrue(exception.message?.contains("got 0") == true)
    }

    // -------------------------------------------------------------------------
    // Validation — mtu
    // -------------------------------------------------------------------------

    @Test
    fun `construct with mtu 1 EXPECT no exception`() {
        // Given / When / Then
        Tun2SocksConfig(socksPort = 10808, mtu = 1)
    }

    @Test
    fun `construct with mtu 0 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            Tun2SocksConfig(socksPort = 10808, mtu = 0)
        }
    }

    @Test
    fun `construct with negative mtu EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            Tun2SocksConfig(socksPort = 10808, mtu = -1)
        }
    }

    // -------------------------------------------------------------------------
    // Validation — ipv4Address / timeouts
    // -------------------------------------------------------------------------

    @Test
    fun `construct with blank ipv4Address EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            Tun2SocksConfig(socksPort = 10808, ipv4Address = "  ")
        }
    }

    @Test
    fun `construct with tcpTimeoutMs 0 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            Tun2SocksConfig(socksPort = 10808, tcpTimeoutMs = 0)
        }
    }

    @Test
    fun `construct with negative udpTimeoutMs EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            Tun2SocksConfig(socksPort = 10808, udpTimeoutMs = -1)
        }
    }
}
