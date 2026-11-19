package mx.edu.utez.wallet.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.edu.utez.wallet.core.StateStore
import mx.edu.utez.wallet.core.WalletState
import java.io.File

/** Demo data survive app restarts in private storage; bearer sessions are never persisted. */
class LocalStateStore(context: Context) : StateStore {
    private val file = AtomicFile(File(context.filesDir, "demo-wallet-v1.json"))
    private val json = Json { encodeDefaults = true }
    override fun load(): WalletState? = if (file.baseFile.exists())
        file.openRead().use { json.decodeFromString<WalletState>(it.readBytes().toString(Charsets.UTF_8)) } else null
    override fun save(state: WalletState) {
        val output = file.startWrite()
        try { output.write(json.encodeToString(state).toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
}
