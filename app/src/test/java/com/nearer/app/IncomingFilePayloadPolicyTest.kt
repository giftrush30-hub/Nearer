package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingFilePayloadPolicyTest {
    @Test fun acceptsPayloadFromOriginalStablePeer() {
        assertTrue(NearbyConnectionsTransport.IncomingFilePayloadPolicy.canAccept("endpoint-1", "peer-a", "endpoint-1", "peer-a"))
    }

    @Test fun acceptsReconnectFromSameStablePeer() {
        assertTrue(NearbyConnectionsTransport.IncomingFilePayloadPolicy.canAccept("endpoint-1", "peer-a", "endpoint-2", "peer-a"))
    }

    @Test fun rejectsDifferentStablePeerEvenWhenEndpointIsReused() {
        assertFalse(NearbyConnectionsTransport.IncomingFilePayloadPolicy.canAccept("endpoint-1", "peer-a", "endpoint-1", "peer-b"))
    }

    @Test fun rejectsDifferentStablePeerAfterReconnect() {
        assertFalse(NearbyConnectionsTransport.IncomingFilePayloadPolicy.canAccept("endpoint-2", "peer-a", "endpoint-3", "peer-b"))
    }

    @Test fun rejectsMissingStableIdentityEvenWhenEndpointIsReused() {
        assertFalse(NearbyConnectionsTransport.IncomingFilePayloadPolicy.canAccept("endpoint-1", "peer-a", "endpoint-1", ""))
    }

    @Test fun legacyPeerMustUseOriginalEndpoint() {
        assertTrue(NearbyConnectionsTransport.IncomingFilePayloadPolicy.canAccept("endpoint-1", "", "endpoint-1", ""))
        assertFalse(NearbyConnectionsTransport.IncomingFilePayloadPolicy.canAccept("endpoint-1", "", "endpoint-2", ""))
    }

    @Test fun firstPayloadMayArriveBeforeMetadata() {
        assertTrue(NearbyConnectionsTransport.IncomingFilePayloadPolicy.canAccept(null, null, "endpoint-1", "peer-a"))
    }

    @Test fun rejectsBlankIncomingEndpoint() {
        assertFalse(NearbyConnectionsTransport.IncomingFilePayloadPolicy.canAccept(null, null, "", "peer-a"))
    }
}
