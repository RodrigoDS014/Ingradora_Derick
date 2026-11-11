package mx.edu.utez.wallet.server

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.edu.utez.wallet.core.StateStore
import mx.edu.utez.wallet.core.WalletState
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Classroom persistence. A process lock prevents two servers from overwriting the same file. */
class FileStateStore(private val file: Path) : StateStore, AutoCloseable {
    private val json = Json { encodeDefaults = true; prettyPrint = true }
    private val channel: FileChannel
    private val lock: java.nio.channels.FileLock
    init {
        Files.createDirectories(file.toAbsolutePath().parent)
        channel = FileChannel.open(file.resolveSibling(file.fileName.toString() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        lock = try { channel.tryLock() ?: error("Otro servidor ya usa este archivo de datos") }
            catch (error: Exception) { channel.close(); throw error }
    }
    override fun load(): WalletState? = if (Files.exists(file)) json.decodeFromString<WalletState>(Files.readString(file)) else null
    override fun save(state: WalletState) {
        val temporary = Files.createTempFile(file.toAbsolutePath().parent, "wallet-", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { output ->
                val buffer = ByteBuffer.wrap(json.encodeToString(state).toByteArray(Charsets.UTF_8))
                while (buffer.hasRemaining()) output.write(buffer)
                output.force(true)
            }
            Files.move(temporary, file.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
    override fun close() { lock.release(); channel.close() }
}
