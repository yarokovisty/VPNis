package org.yarokovisty.vpnis.data.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TunConfigTest {

    // -------------------------------------------------------------------------
    // Defaults — construction must not throw; field values must match spec
    // -------------------------------------------------------------------------

    @Test
    fun `construct with all defaults EXPECT no exception`() {
        // Given / When / Then — construction must not throw
        TunConfig()
    }

    @Test
    fun `construct with all defaults EXPECT clientAddress is 10_0_0_2`() {
        // Given
        val config = TunConfig()

        // When / Then
        assertEquals("10.0.0.2", config.clientAddress)
    }

    @Test
    fun `construct with all defaults EXPECT prefixLength is 30`() {
        // Given
        val config = TunConfig()

        // When / Then
        assertEquals(30, config.prefixLength)
    }

    @Test
    fun `construct with all defaults EXPECT mtu is 8500`() {
        // Given
        val config = TunConfig()

        // When / Then
        assertEquals(8500, config.mtu)
    }

    @Test
    fun `construct with all defaults EXPECT dnsServers are cloudflare addresses`() {
        // Given
        val config = TunConfig()

        // When / Then
        assertEquals(listOf("1.1.1.1", "1.0.0.1"), config.dnsServers)
    }

    @Test
    fun `construct with all defaults EXPECT routes contain both IPv4 and IPv6 catch-all routes`() {
        // Given
        val config = TunConfig()

        // When / Then — IPv6 ::/0 is present alongside IPv4 for fail-closed leak prevention (#106)
        assertEquals(listOf("0.0.0.0/0", "::/0"), config.routes)
    }

    @Test
    fun `construct with all defaults EXPECT ipv6ClientAddress is fd00__1`() {
        // Given
        val config = TunConfig()

        // When / Then
        assertEquals("fd00::1", config.ipv6ClientAddress)
    }

    @Test
    fun `construct with all defaults EXPECT ipv6PrefixLength is 128`() {
        // Given
        val config = TunConfig()

        // When / Then
        assertEquals(128, config.ipv6PrefixLength)
    }

    @Test
    fun `construct with all defaults EXPECT localSocksPort is 10808`() {
        // Given
        val config = TunConfig()

        // When / Then
        assertEquals(10808, config.localSocksPort)
    }

    // -------------------------------------------------------------------------
    // prefixLength — boundary validation
    // -------------------------------------------------------------------------

    @Test
    fun `construct with prefixLength 0 EXPECT no exception`() {
        // Given / When / Then
        TunConfig(prefixLength = 0)
    }

    @Test
    fun `construct with prefixLength 32 EXPECT no exception`() {
        // Given / When / Then
        TunConfig(prefixLength = 32)
    }

    @Test
    fun `construct with prefixLength -1 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(prefixLength = -1)
        }
    }

    @Test
    fun `construct with prefixLength 33 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(prefixLength = 33)
        }
    }

    @Test
    fun `construct with prefixLength -1 EXPECT error message matches spec`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TunConfig(prefixLength = -1)
        }

        // Then
        assertEquals("prefixLength must be in 0..32, got -1", exception.message)
    }

    @Test
    fun `construct with prefixLength 33 EXPECT error message matches spec`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TunConfig(prefixLength = 33)
        }

        // Then
        assertEquals("prefixLength must be in 0..32, got 33", exception.message)
    }

    // -------------------------------------------------------------------------
    // ipv6PrefixLength — boundary validation (independent of IPv4 0..32)
    // -------------------------------------------------------------------------

    @Test
    fun `construct with ipv6PrefixLength 0 EXPECT no exception`() {
        // Given / When / Then
        TunConfig(ipv6PrefixLength = 0)
    }

    @Test
    fun `construct with ipv6PrefixLength 128 EXPECT no exception`() {
        // Given / When / Then — /128 must NOT fail (would if validated against IPv4 max of 32)
        TunConfig(ipv6PrefixLength = 128)
    }

    @Test
    fun `construct with ipv6PrefixLength 129 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(ipv6PrefixLength = 129)
        }
    }

    @Test
    fun `construct with ipv6PrefixLength -1 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(ipv6PrefixLength = -1)
        }
    }

    @Test
    fun `construct with ipv6PrefixLength 129 EXPECT error message matches spec`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TunConfig(ipv6PrefixLength = 129)
        }

        // Then
        assertEquals("ipv6PrefixLength must be in 0..128, got 129", exception.message)
    }

    @Test
    fun `construct with blank ipv6ClientAddress EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(ipv6ClientAddress = "   ")
        }
    }

    @Test
    fun `construct with IPv4 prefixLength 33 EXPECT still rejected after IPv6 added`() {
        // Given / When / Then — IPv6 addition must NOT loosen IPv4's 0..32 constraint
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(prefixLength = 33)
        }
    }

    // -------------------------------------------------------------------------
    // mtu — boundary validation
    // -------------------------------------------------------------------------

    @Test
    fun `construct with mtu 1 EXPECT no exception`() {
        // Given / When / Then
        TunConfig(mtu = 1)
    }

    @Test
    fun `construct with mtu 0 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(mtu = 0)
        }
    }

    @Test
    fun `construct with negative mtu EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(mtu = -1)
        }
    }

    @Test
    fun `construct with mtu 0 EXPECT error message matches spec`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TunConfig(mtu = 0)
        }

        // Then
        assertEquals("mtu must be positive, got 0", exception.message)
    }

    @Test
    fun `construct with mtu -1 EXPECT error message matches spec`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TunConfig(mtu = -1)
        }

        // Then
        assertEquals("mtu must be positive, got -1", exception.message)
    }

    // -------------------------------------------------------------------------
    // localSocksPort — boundary validation
    // -------------------------------------------------------------------------

    @Test
    fun `construct with localSocksPort 1 EXPECT no exception`() {
        // Given / When / Then
        TunConfig(localSocksPort = 1)
    }

    @Test
    fun `construct with localSocksPort 65535 EXPECT no exception`() {
        // Given / When / Then
        TunConfig(localSocksPort = 65535)
    }

    @Test
    fun `construct with localSocksPort 0 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(localSocksPort = 0)
        }
    }

    @Test
    fun `construct with localSocksPort 65536 EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(localSocksPort = 65536)
        }
    }

    @Test
    fun `construct with localSocksPort 0 EXPECT error message matches spec`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TunConfig(localSocksPort = 0)
        }

        // Then
        assertEquals("localSocksPort must be in 1..65535, got 0", exception.message)
    }

    @Test
    fun `construct with localSocksPort 65536 EXPECT error message matches spec`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TunConfig(localSocksPort = 65536)
        }

        // Then
        assertEquals("localSocksPort must be in 1..65535, got 65536", exception.message)
    }

    // -------------------------------------------------------------------------
    // clientAddress — blank validation
    // -------------------------------------------------------------------------

    @Test
    fun `construct with empty clientAddress EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(clientAddress = "")
        }
    }

    @Test
    fun `construct with blank clientAddress EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(clientAddress = "   ")
        }
    }

    @Test
    fun `construct with empty clientAddress EXPECT error message matches spec`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TunConfig(clientAddress = "")
        }

        // Then
        assertEquals("clientAddress must not be blank", exception.message)
    }

    // -------------------------------------------------------------------------
    // dnsServers — blank entry validation
    // -------------------------------------------------------------------------

    @Test
    fun `construct with dnsServers containing blank entry EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(dnsServers = listOf("1.1.1.1", ""))
        }
    }

    @Test
    fun `construct with dnsServers containing whitespace entry EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(dnsServers = listOf("   "))
        }
    }

    @Test
    fun `construct with dnsServers containing blank entry EXPECT error message matches spec`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TunConfig(dnsServers = listOf("1.1.1.1", ""))
        }

        // Then
        assertEquals("dnsServers must not contain blank entries", exception.message)
    }

    @Test
    fun `construct with empty dnsServers list EXPECT no exception`() {
        // Given / When / Then — all{} on empty list is vacuously true; empty list is allowed
        TunConfig(dnsServers = emptyList())
    }

    // -------------------------------------------------------------------------
    // routes — blank entry validation
    // -------------------------------------------------------------------------

    @Test
    fun `construct with routes containing blank entry EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(routes = listOf("0.0.0.0/0", "   "))
        }
    }

    @Test
    fun `construct with routes containing blank entry EXPECT error message matches spec`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TunConfig(routes = listOf(""))
        }

        // Then
        assertEquals("routes must not contain blank entries", exception.message)
    }

    // -------------------------------------------------------------------------
    // session — blank validation
    // -------------------------------------------------------------------------

    @Test
    fun `construct with blank session EXPECT IllegalArgumentException`() {
        // Given / When / Then
        assertThrows(IllegalArgumentException::class.java) {
            TunConfig(session = "   ")
        }
    }

    @Test
    fun `construct with empty session EXPECT error message matches spec`() {
        // Given / When
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TunConfig(session = "")
        }

        // Then
        assertEquals("session must not be blank", exception.message)
    }

    // -------------------------------------------------------------------------
    // toTun2SocksConfig — field mapping
    // -------------------------------------------------------------------------

    @Test
    fun `toTun2SocksConfig EXPECT socksPort mapped from localSocksPort`() {
        // Given
        val config = TunConfig(localSocksPort = 1080, mtu = 1500)

        // When
        val result = config.toTun2SocksConfig()

        // Then
        assertEquals(1080, result.socksPort)
    }

    @Test
    fun `toTun2SocksConfig EXPECT mtu mapped from TunConfig mtu`() {
        // Given
        val config = TunConfig(localSocksPort = 1080, mtu = 1500)

        // When
        val result = config.toTun2SocksConfig()

        // Then
        assertEquals(1500, result.mtu)
    }

    @Test
    fun `toTun2SocksConfig EXPECT socksAddress is default 127_0_0_1`() {
        // Given
        val config = TunConfig(localSocksPort = 1080, mtu = 1500)

        // When
        val result = config.toTun2SocksConfig()

        // Then
        assertEquals("127.0.0.1", result.socksAddress)
    }

    @Test
    fun `toTun2SocksConfig EXPECT udpMode is default udp`() {
        // Given
        val config = TunConfig(localSocksPort = 1080, mtu = 1500)

        // When
        val result = config.toTun2SocksConfig()

        // Then
        assertEquals("udp", result.udpMode)
    }

    @Test
    fun `toTun2SocksConfig EXPECT taskStackSize is default 20480`() {
        // Given
        val config = TunConfig(localSocksPort = 1080, mtu = 1500)

        // When
        val result = config.toTun2SocksConfig()

        // Then
        assertEquals(20480, result.taskStackSize)
    }

    @Test
    fun `toTun2SocksConfig EXPECT logLevel is default warn`() {
        // Given
        val config = TunConfig(localSocksPort = 1080, mtu = 1500)

        // When
        val result = config.toTun2SocksConfig()

        // Then
        assertEquals("warn", result.logLevel)
    }
}
