package com.privatemessenger.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Base64

/**
 * Device identity key kept in Android Keystore.
 * The private key is non-exportable; only the public key is safe to publish.
 */
object DeviceIdentity {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "private_messenger_identity_ed25519_v1"

    fun getOrCreate(): KeyPair {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) return KeyPair(existing.certificate.publicKey, existing.privateKey)

        val generator = KeyPairGenerator.getInstance("Ed25519", ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setDigests(KeyProperties.DIGEST_NONE).build()
        )
        return generator.generateKeyPair()
    }

    fun publicKeyBase64(): String = Base64.getEncoder().encodeToString(getOrCreate().public.encoded)
}
