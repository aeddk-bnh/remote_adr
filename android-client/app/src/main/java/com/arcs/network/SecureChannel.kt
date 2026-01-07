package com.arcs.client.network

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * AES-256-GCM encryption for payload security
 */
class SecureChannel(
    private val keyAlias: String = "arcs_session_key"
) {
    
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12  // 96 bits
        private const val TAG_SIZE = 128  // 128 bits
    }
    
    private val secureRandom = SecureRandom()
    
    /**
     * Generate session key from JWT token
     */
    fun deriveKeyFromToken(jwtToken: String, deviceId: String): SecretKey {
        // Use PBKDF2 for key derivation
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(
            jwtToken.toCharArray(),
            deviceId.toByteArray(),
            100000,  // iterations
            256  // key length
        )
        val key = factory.generateSecret(spec)
        return SecretKeySpec(key.encoded, "AES")
    }
    
    /**
     * Encrypt data with AES-256-GCM
     * Returns: [IV: 12 bytes][Ciphertext + Auth Tag]
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        try {
            // Generate random IV
            val iv = ByteArray(IV_SIZE)
            secureRandom.nextBytes(iv)
            
            // Initialize cipher
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
            
            // Encrypt
            val ciphertext = cipher.doFinal(plaintext)
            
            // Combine IV + ciphertext
            val result = ByteArray(IV_SIZE + ciphertext.size)
            System.arraycopy(iv, 0, result, 0, IV_SIZE)
            System.arraycopy(ciphertext, 0, result, IV_SIZE, ciphertext.size)
            
            return result
            
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            throw e
        }
    }
    
    /**
     * Decrypt data with AES-256-GCM
     * Input format: [IV: 12 bytes][Ciphertext + Auth Tag]
     */
    fun decrypt(encrypted: ByteArray, key: SecretKey): ByteArray {
        try {
            if (encrypted.size < IV_SIZE + TAG_SIZE / 8) {
                throw IllegalArgumentException("Invalid encrypted data size")
            }
            
            // Extract IV
            val iv = encrypted.copyOfRange(0, IV_SIZE)
            
            // Extract ciphertext
            val ciphertext = encrypted.copyOfRange(IV_SIZE, encrypted.size)
            
            // Initialize cipher
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
            
            // Decrypt and verify
            return cipher.doFinal(ciphertext)
            
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            throw e
        }
    }
    
    /**
     * Store key in Android Keystore (for device secret)
     */
    fun generateAndStoreKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        
        val keyGenSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        
        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }
    
    /**
     * Retrieve key from Android Keystore
     */
    fun retrieveKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.getKey(keyAlias, null) as? SecretKey
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve key")
            null
        }
    }
    
    /**
     * Delete key from Keystore
     */
    fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(keyAlias)
            Timber.i("Key deleted: $keyAlias")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete key")
        }
    }
}
