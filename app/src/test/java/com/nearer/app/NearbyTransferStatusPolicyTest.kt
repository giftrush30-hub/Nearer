package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyTransferStatusPolicyTest {
    @Test fun recognizesLegacyVerifiedCompletion() {
        assertTrue(NearbyTransferStatusPolicy.isVerifiedOutgoingCompletion("File sent to the nearby phone: video.mp4"))
    }

    @Test fun recognizesHyperTransferReceiptCompletion() {
        assertTrue(NearbyTransferStatusPolicy.isVerifiedOutgoingCompletion("Verified and saved on the recipient's phone: video.mp4"))
    }

    @Test fun doesNotTreatProgressOrRetryAsCompletion() {
        assertFalse(NearbyTransferStatusPolicy.isVerifiedOutgoingCompletion("Sending video.mp4 • 100%"))
        assertFalse(NearbyTransferStatusPolicy.isVerifiedOutgoingCompletion("The recipient could not verify the file. NeaRer is retrying safely…"))
        assertFalse(NearbyTransferStatusPolicy.isVerifiedOutgoingCompletion("Nearby file transfer paused. Reconnect to the same phone to resume."))
    }
}
