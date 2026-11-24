package mx.edu.utez.wallet.sensors.nfc

/**
 * Pase armado para emisión HCE. Solo en memoria: nunca se escribe a disco y se descarta al vencer.
 * El servicio HCE no tiene red, así que la app arma el token ANTES de acercar el teléfono.
 */
object HcePassStore {
    private class Armed(val token: ByteArray, val expiresAtMillis: Long)

    @Volatile private var armed: Armed? = null

    fun arm(token: String, expiresAtMillis: Long) {
        armed = Armed(token.toByteArray(Charsets.UTF_8), expiresAtMillis)
    }

    fun clear() { armed = null }

    fun current(now: Long = System.currentTimeMillis()): ByteArray? {
        val value = armed ?: return null
        if (now >= value.expiresAtMillis) { armed = null; return null }
        return value.token
    }
}
