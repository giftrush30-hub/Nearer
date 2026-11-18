package com.nearer.app

import android.content.Context
import java.security.KeyPair
import java.security.MessageDigest

/**
 * Trust gate between the existing NeaRer device identity system and the
 * HyperTransfer AEAD channel.
 *
 * Call prepare() on each device, exchange the resulting signature over the
 * exact transcript, then call authenticate() only after the user has approved
 * this file transfer. First-contact peers additionally require explicit
 * firstContactApproved before their key pair is pinned.
 */
internal object HyperTransferSessionAuthenticator {
    data class Prepared(
        val transferId: String,
        val localId: String,
        val remoteId: String,
        val localIsSender: Boolean,
        val transcript: ByteArray,
        val localSignature: ByteArray,
        val localEphemeralKeyPair: KeyPair,
        val remoteEphemeralPublicKey: java.security.PublicKey,
        val remoteSigningPublicKey: ByteArray,
        val remoteRecipientEncryptionPublicKey: ByteArray
    )

    fun prepare(
        transferId: String,
        localMaterial: HyperTransferSessionTranscript.PeerMaterial,
        remoteMaterial: HyperTransferSessionTranscript.PeerMaterial,
        localIsSender: Boolean,
        localEphemeralKeyPair: KeyPair
    ): Prepared {
        val localId = requireNotNull(NeaRerIdentity.normalize(localMaterial.peerId)) {
            "Invalid local NeaRer identity"
        }
        val remoteId = requireNotNull(NeaRerIdentity.normalize(remoteMaterial.peerId)) {
            "Invalid remote NeaRer identity"
        }
        require(localId != remoteId)
        require(MessageDigest.isEqual(localMaterial.signingPublicKey, SecurePeerIdentity.publicKeyEncoded())) {
            "Local signing identity does not match the device-bound NeaRer identity"
        }
        require(MessageDigest.isEqual(localMaterial.ephemeralPublicKey, localEphemeralKeyPair.public.encoded)) {
            "Local ephemeral public key does not match its private key"
        }
        val remoteEphemeral = NearbyMessageCrypto.decodeEphemeralPublicKey(remoteMaterial.ephemeralPublicKey)
        NearbyMessageCrypto.decodeEphemeralPublicKey(remoteMaterial.encryptionPublicKey)
        require(SecurePeerIdentity.isValidPublicKey(remoteMaterial.signingPublicKey)) {
            "Remote signing key is invalid"
        }
        val transcript = HyperTransferSessionTranscript.create(
            transferId, localMaterial, remoteMaterial, localIsSender
        )
        val signature = SecurePeerIdentity.signDetached(transcript)
        return Prepared(
            transferId = transferId,
            localId = localId,
            remoteId = remoteId,
            localIsSender = localIsSender,
            transcript = transcript,
            localSignature = signature,
            localEphemeralKeyPair = localEphemeralKeyPair,
            remoteEphemeralPublicKey = remoteEphemeral,
            remoteSigningPublicKey = remoteMaterial.signingPublicKey.copyOf(),
            remoteRecipientEncryptionPublicKey = remoteMaterial.encryptionPublicKey.copyOf()
        )
    }

    /**
     * Returns an AEAD channel only after the remote signature matches the
     * transfer-specific transcript, TOFU pins are consistent, and the caller
     * confirms that the user approved this transfer. First-contact pinning is
     * delayed until explicit first-contact approval is also supplied.
     */
    fun authenticate(
        context: Context,
        prepared: Prepared,
        remoteSignature: ByteArray,
        transferApproved: Boolean,
        firstContactApproved: Boolean
    ): HyperTransferSecureChannel {
        require(transferApproved) { "The user has not approved this file transfer" }
        require(remoteSignature.size in 8..128) { "Invalid remote identity signature" }
        require(SecurePeerIdentity.verifyDetached(
            prepared.remoteSigningPublicKey, prepared.transcript, remoteSignature
        )) { "Remote peer did not authenticate this HyperTransfer session" }

        val trust = TrustedPeerKeyStore.checkPinned(
            context,
            prepared.remoteId,
            prepared.remoteSigningPublicKey,
            prepared.remoteRecipientEncryptionPublicKey
        )
        when (trust) {
            TrustedPeerKeyStore.Result.MATCH -> Unit
            TrustedPeerKeyStore.Result.FIRST_SEEN -> {
                require(firstContactApproved) {
                    "First-contact identity approval is required before pinning this peer"
                }
                require(TrustedPeerKeyStore.checkAndPin(
                    context,
                    prepared.remoteId,
                    prepared.remoteSigningPublicKey,
                    prepared.remoteRecipientEncryptionPublicKey
                ) == TrustedPeerKeyStore.Result.FIRST_SEEN) {
                    "Could not safely pin the first-contact peer identity"
                }
            }
            TrustedPeerKeyStore.Result.KEY_CHANGED ->
                throw SecurityException("The saved NeaRer identity key changed")
            TrustedPeerKeyStore.Result.STORAGE_ERROR ->
                throw IllegalStateException("NeaRer could not safely verify the peer identity")
        }

        return HyperTransferSecureChannel.establish(
            localPrivateKey = prepared.localEphemeralKeyPair.private,
            remoteEphemeralPublicKey = prepared.remoteEphemeralPublicKey,
            transcript = prepared.transcript,
            transferId = prepared.transferId,
            senderId = if (prepared.localIsSender) prepared.localId else prepared.remoteId,
            receiverId = if (prepared.localIsSender) prepared.remoteId else prepared.localId,
            localIsSender = prepared.localIsSender
        )
    }
}
