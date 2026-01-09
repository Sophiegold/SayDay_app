package com.sophiegold.app_sayday

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object LockManager {
    private const val ENCRYPTED_PREFS_NAME = "lock_prefs_encrypted"
    private const val PLAIN_PREFS_NAME = "lock_prefs_simple" // legacy (if any)
    const val LOCK_ENABLED_KEY = "lock_enabled"
    private const val LOCK_SALT_KEY = "lock_salt"
    private const val LOCK_HASH_KEY = "lock_hash"

    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun initialize(context: Context) {
        try {
            // Force creation of MasterKey and EncryptedSharedPreferences now
            getEncryptedPrefs(context)
        } catch (e: Exception) {
            // Log but don't crash; initialization can be retried later
            Log.w("LockManager", "initialize: failed to create encrypted prefs: ${e.message}")
        }
    }

    fun isLockEnabled(context: Context): Boolean {
        return try {
            getEncryptedPrefs(context).getBoolean(LOCK_ENABLED_KEY, false)
        } catch (e: Exception) {
            Log.w("LockManager", "isLockEnabled: failed reading prefs: ${e.message}")
            false
        }
    }

    fun savePassword(context: Context, password: CharArray) {
        var salt: ByteArray? = null
        var hash: ByteArray? = null
        try {
            salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            hash = pbkdf2(password, salt)

            val enc = getEncryptedPrefs(context)
            enc.edit()
                .putString(LOCK_SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(LOCK_HASH_KEY, Base64.encodeToString(hash, Base64.NO_WRAP))
                .putBoolean(LOCK_ENABLED_KEY, true)
                .apply()
        } catch (e: Exception) {
            Log.w("LockManager", "savePassword: error saving lock: ${e.message}")
        } finally {
            // wipe sensitive material
            password.fill('\u0000')
            salt?.fill(0)
            hash?.fill(0)
        }
    }

    fun clearLock(context: Context) {
        try {
            val enc = getEncryptedPrefs(context)
            enc.edit()
                .remove(LOCK_SALT_KEY)
                .remove(LOCK_HASH_KEY)
                .putBoolean(LOCK_ENABLED_KEY, false)
                .apply()
        } catch (e: Exception) {
            Log.w("LockManager", "clearLock: failed to clear lock: ${e.message}")
        }
    }

    fun verifyPassword(context: Context, password: CharArray): Boolean {
        var saltB64: String? = null
        var hashB64: String? = null
        var pwdCopy: CharArray? = null
        try {
            val enc = getEncryptedPrefs(context)
            saltB64 = enc.getString(LOCK_SALT_KEY, null)
            hashB64 = enc.getString(LOCK_HASH_KEY, null)
            if (saltB64 == null || hashB64 == null) return false

            // Work on a local copy; never mutate the caller’s array
            pwdCopy = password.copyOf()

            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val expected = Base64.decode(hashB64, Base64.NO_WRAP)
            val got = pbkdf2(pwdCopy, salt)

            val result = MessageDigest.isEqual(expected, got)

            got.fill(0)
            return result
        } catch (e: Exception) {
            Log.w("LockManager", "verifyPassword: error during verify: ${e.message}")
            return false
        } finally {
            // Wipe only the local copy
            pwdCopy?.fill('\u0000')
            // Do NOT wipe the original `password` here
            saltB64 = null
            hashB64 = null
        }
    }


    private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
        var spec: PBEKeySpec? = null
        try {
            spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
            // Try SHA-256 first, fallback to SHA1 if not available
            return try {
                val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                skf.generateSecret(spec).encoded
            } catch (e: Exception) {
                Log.w("LockManager", "PBKDF2WithHmacSHA256 not available, falling back to SHA1: ${e.message}")
                val skf2 = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                skf2.generateSecret(spec).encoded
            }
        } finally {
            // clear PBEKeySpec password contents where possible
            try {
                spec?.clearPassword()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * If you had earlier plain SharedPreferences saved under PLAIN_PREFS_NAME (e.g., during testing),
     * this will copy the already-hashed values into encrypted prefs and delete the plain prefs.
     */
    fun migratePlainIfNeeded(context: Context) {
        try {
            val plain = context.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)
            val plainHas = plain.contains(LOCK_SALT_KEY) && plain.contains(LOCK_HASH_KEY)
            val enc = getEncryptedPrefs(context)
            val encHas = enc.contains(LOCK_SALT_KEY) && enc.contains(LOCK_HASH_KEY)
            if (plainHas && !encHas) {
                val salt = plain.getString(LOCK_SALT_KEY, null)
                val hash = plain.getString(LOCK_HASH_KEY, null)
                val enabled = plain.getBoolean(LOCK_ENABLED_KEY, false)
                if (salt != null && hash != null) {
                    enc.edit()
                        .putString(LOCK_SALT_KEY, salt)
                        .putString(LOCK_HASH_KEY, hash)
                        .putBoolean(LOCK_ENABLED_KEY, enabled)
                        .apply()
                    // remove old plain data
                    plain.edit().remove(LOCK_SALT_KEY).remove(LOCK_HASH_KEY).remove(LOCK_ENABLED_KEY).apply()
                }
            }
        } catch (e: Exception) {
            Log.w("LockManager", "migratePlainIfNeeded: ${e.message}")
        }
    }
}