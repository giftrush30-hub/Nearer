package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyEndpointNameCodecTest {
    @Test fun decodesLegacyNameWithoutStableIdentity() {
        assertEquals("Tunde" to "", NearbyEndpointNameCodec.decode("Tunde"))
    }

    @Test fun limitsLegacyDisplayNameToTwentyThreeCharacters() {
        assertEquals(("A".repeat(23)) to "", NearbyEndpointNameCodec.decode("A".repeat(30)))
    }

    @Test fun preservesDisplayNameBeforeLastSeparatorWhenIdentityIsValid() {
        val identity = NeaRerIdentity.getOrCreate(null)
        assertEquals("Tunde|Work" to identity, NearbyEndpointNameCodec.decode("Tunde|Work|$identity"))
    }

    @Test fun invalidIdentityFallsBackToLegacyDisplayName() {
        assertEquals("Tunde|invalid" to "", NearbyEndpointNameCodec.decode("Tunde|invalid"))
    }

    @Test fun rejectsEmptyNamePrefixAsIdentityEnvelope() {
        assertEquals("|invalid" to "", NearbyEndpointNameCodec.decode("|invalid"))
    }
}
