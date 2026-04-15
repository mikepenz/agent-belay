package com.mikepenz.agentapprover.storage

import co.touchlab.kermit.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileAttribute
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
 * (see SECURITY.md). In both cases the protection is defense-in-depth:
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
        writeKeyAtomically(dir, keyFile, key.encoded)
        logger.i { "Generated new database key" }
        return key
    }

    /**
     * Writes [keyBytes] to [keyFile] via a temp file + atomic rename so a crash
     * or partial write cannot leave a truncated key behind. On POSIX the temp
     * file is created with `rw-------` up front (so there is no observable
     * window where the key is world-readable). On Windows the file inherits
     * the user's default ACL — documented limitation in SECURITY.md.
     */
    private fun writeKeyAtomically(dir: File, keyFile: File, keyBytes: ByteArray) {
        val dirPath = dir.toPath()
        val posixAttr: FileAttribute<*>? = try {
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
            null
        }
        val temp = if (posixAttr != null) {
            Files.createTempFile(dirPath, "db.key", ".tmp", posixAttr)
        } else {
            Files.createTempFile(dirPath, "db.key", ".tmp")
        }
        try {
            Files.write(temp, keyBytes)
            try {
                Files.move(temp, keyFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: UnsupportedOperationException) {
                Files.move(temp, keyFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            // Re-assert permissions on the final path (ATOMIC_MOVE preserves them
            // on most POSIX filesystems, but some tmpfs implementations drop
            // them; this is a defensive no-op on platforms where they're kept).
            applyOwnerOnlyPermissions(keyFile)
        } catch (e: Exception) {
            try { Files.deleteIfExists(temp) } catch (_: Exception) { /* best effort */ }
            throw e
        }
    }

    private fun applyOwnerOnlyPermissions(file: File) {
        try {
            val perms = PosixFilePermissions.fromString("rw-------")
            Files.setPosixFilePermissions(file.toPath(), perms)
        } catch (_: UnsupportedOperationException) {
            // Windows or another non-POSIX filesystem. Documented limitation —
            // the file inherits the user's default ACL instead.
        } catch (e: Exception) {
            logger.w { "Failed to set 0600 permissions on db key file: ${e.message}" }
        }
    }

}
