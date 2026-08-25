package com.privatemessenger.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Base64

/**
 * Device keys stay inside Android Keystore. Private key material is never
 * serialized to Supabase. Public keys can be registered with the backend.
 *
 * P-256 is used here for broad Android API compatibility. The protocol layer
 * must use the agreement key only for ECDH and the identity key only for
 * signatures; message encryption is handled separately.
 */
object DeviceIdentity {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val IDENTITY_ALIAS = "private_messenger_identity_p256_v1"
    private const val AGREEMENT_ALIAS = "private_messenger_agreement_p256_v1"

    private fun create(alias: String, purposes: Int): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, purposes)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .build()
        return generator.generateKeyPair()
    }

    private fun getOrCreate(alias: String, purposes: Int): KeyPair {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        return if (existing != null) {
            KeyPair(existing.certificate.publicKey, existing.privateKey)
        } else {
            create(alias, purposes)
        }
    }

    fun identityKeyPair(): KeyPair = getOrCreate(
        IDENTITY_ALIAS,
        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
    )

    fun agreementKeyPair(): KeyPair = getOrCreate(
        AGREEMENT_ALIAS,
        KeyProperties.PURPOSE_AGREE_KEY
    )

    fun identityPublicKeyBase64(): String =
        Base64.getEncoder().encodeToString(identityKeyPair().public.encoded)

    fun agreementPublicKeyBase64(): String =
        Base64.getEncoder().encodeToString(agreementKeyPair().public.encoded)
}
