package com.mikepenz.agentapprover.storage

import co.touchlab.kermit.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Manages the long-lived AES-256 key used by [ColumnCipher] to encrypt
 * sensitive history columns at rest.
 *
 * The key is stored in a single binary file at `<dataDir>/db.key`. On POSIX
 * platforms the file is created with `rw-------` (0600). On Windows POSIX
 * permissions are not supported by the JVM file attribute view, so the file
 * inherits the user's default ACL — this is documented as a known limitation
 * (see SECURITY_COPILOT.md). In both cases the protection is defense-in-depth:
 * any process running as the same user can still read the key file, so this
 * should not be treated as a credential vault.
 */
object DbKeyManager {

    private val logger = Logger.withTag("DbKeyManager")
    private const val KEY_FILE_NAME = "db.key"
    private const val KEY_BITS = 256
    private const val KEY_BYTES = KEY_BITS / 8
    private const val ALGORITHM = "AES"

    /**
     * Returns the AES key for [dataDir], generating and persisting a new
     * 256-bit random key on first call. Subsequent calls return the same
     * key bytes loaded from disk.
     */
    fun loadOrCreate(dataDir: String): SecretKey {
        val dir = File(dataDir).also { if (!it.exists()) it.mkdirs() }
        val keyFile = File(dir, KEY_FILE_NAME)
        if (keyFile.exists()) {
            val bytes = keyFile.readBytes()
            require(bytes.size == KEY_BYTES) {
                "Existing $KEY_FILE_NAME has unexpected length ${bytes.size} (expected $KEY_BYTES)"
            }
            return SecretKeySpec(bytes, ALGORITHM)
        }

        val generator = KeyGenerator.getInstance(ALGORITHM).apply { init(KEY_BITS) }
        val key = generator.generateKey()
        keyFile.writeBytes(key.encoded)
        applyOwnerOnlyPermissions(keyFile)
        logger.i { "Generated new database key at ${keyFile.absolutePath}" }
        return key
    }

    private fun applyOwnerOnlyPermissions(file: File) {
        try {
            val perms = PosixFilePermissions.fromString("rw-------")
            Files.setPosixFilePermissions(file.toPath(), perms)
        } catch (_: UnsupportedOperationException) {
            // Windows or another non-POSIX filesystem. Documented limitation —
            // the file inherits the user's default ACL instead.
        } catch (e: Exception) {
            logger.w { "Failed to set 0600 permissions on ${file.absolutePath}: ${e.message}" }
        }
    }

}
