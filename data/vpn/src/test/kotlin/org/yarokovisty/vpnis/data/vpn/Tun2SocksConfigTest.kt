package org.yarokovisty.vpnis.data.vpn

import org.junit.Assert.assertEquals
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
                "socks5:\n" +
                "  address: 127.0.0.1\n" +
                "  port: 10808\n" +
                "  udp: 'udp'\n" +
                "misc:\n" +
                "  task-stack-size: 20480\n" +
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
        val config = Tun2SocksConfig(socksPort = 10808, mtu = 1500)

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.contains("  mtu: 1500\n"))
    }

    @Test
    fun `toYaml with custom taskStackSize EXPECT task-stack-size line reflects override`() {
        // Given
        val config = Tun2SocksConfig(socksPort = 10808, taskStackSize = 4096)

        // When
        val yaml = config.toYaml()

        // Then
        assertTrue(yaml.contains("  task-stack-size: 4096\n"))
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
            mtu = 1500,
            udpMode = "tcp",
            taskStackSize = 4096,
            logLevel = "error",
        )

        // When
        val yaml = config.toYaml()

        // Then
        val expected =
            "tunnel:\n" +
                "  mtu: 1500\n" +
                "socks5:\n" +
                "  address: 192.168.1.1\n" +
                "  port: 9090\n" +
                "  udp: 'tcp'\n" +
                "misc:\n" +
                "  task-stack-size: 4096\n" +
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
    // Validation — taskStackSize
    // -------------------------------------------------------------------------

    @Test
    fun `construct with taskStackSize 1 EXPECT no exception`() {
        // Given / When / Then
        Tun2SocksConfig(socksPort = 10808, taskStackSize = 1)
    }

    @Test
    fun `construct with taskStackSize 0 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            Tun2SocksConfig(socksPort = 10808, taskStackSize = 0)
        }
    }

    @Test
    fun `construct with negative taskStackSize EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            Tun2SocksConfig(socksPort = 10808, taskStackSize = -1024)
        }
    }
}
