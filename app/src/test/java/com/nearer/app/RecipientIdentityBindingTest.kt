package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class RecipientIdentityBindingTest {
    private fun pair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    @Test fun bindingMustBeSignedByPinnedSigningIdentity() {
        val signing = pair()
        val attacker = pair()
        val encryption = pair()
        val binding = RecipientIdentityBinding.create(
            "AAAAAA", signing.public.encoded, encryption.public.encoded,
            1L, 1000L, 5000L
        ) { bytes ->
            Signature.getInstance("SHA256withECDSA").run {
                initSign(signing.private); update(bytes); sign()
            }
        }
        assertTrue(binding.verify(signing.public.encoded, 2000L))
        assertFalse(binding.verify(attacker.public.encoded, 2000L))
        assertFalse(binding.copy(sequence = 2L).verify(signing.public.encoded, 2000L))
        assertFalse(binding.verify(signing.public.encoded, 5000L))
    }
}
