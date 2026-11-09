package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class FastShareHostAuthorityTest {
    @Test fun acceptsHostnamesIpv4AndIpv6WithValidPorts() {
        listOf("localhost", "phone.local", "nearer-device.example.com:8765",
            "192.168.43.1:8765", "[::1]:8765", "[2001:db8::1]").forEach {
            assertTrue("Expected valid authority: $it", FastShareHostAuthority.isValid(it))
        }
    }

    @Test fun rejectsMalformedPortsAndUnbracketedIpv6() {
        listOf("phone.local:", "phone.local:0", "phone.local:65536", "phone.local:abc",
            "phone.local:80:90", "2001:db8::1", "[not-ipv6]:80", "[::1]:abc", "[::1]:65536").forEach {
            assertFalse("Expected invalid authority: $it", FastShareHostAuthority.isValid(it))
        }
    }

    @Test fun rejectsInvalidOrAmbiguousIpv4Authorities() {
        listOf("256.1.2.3", "1.999.2.3", "1.2.3.999", "01.2.3.4",
            "1.02.3.4", "1.2.003.4", "1.2.3.04").forEach {
            assertFalse("Expected invalid IPv4 authority: $it", FastShareHostAuthority.isValid(it))
        }
    }

    @Test fun rejectsMalformedDnsLabelsAndEmptyAuthority() {
        listOf("", ".", "-phone.local", "phone-.local", "two..labels", "bad_host.local", " phone.local").forEach {
            assertFalse("Expected invalid authority: $it", FastShareHostAuthority.isValid(it))
        }
    }
    @Test fun rejectsBracketedHostnamesAndScopedIpv6ZoneIds() {
        listOf("[localhost]", "[example.com]:80", "[fe80::1%eth0]").forEach {
            assertFalse("Expected invalid bracketed host: $it", FastShareHostAuthority.isValid(it))
        }
    }

}
