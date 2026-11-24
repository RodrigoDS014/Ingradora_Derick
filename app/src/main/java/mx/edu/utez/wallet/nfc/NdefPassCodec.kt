package mx.edu.utez.wallet.sensors.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord

/** Formato NDEF del pase: un registro MIME. Al leer también acepta un registro de texto. */
internal object NdefPassCodec {
    const val MIME_TYPE = "application/vnd.utez.wallet.pass"
    const val MAX_PAYLOAD = 2048

    fun build(token: String): NdefMessage =
        NdefMessage(NdefRecord.createMime(MIME_TYPE, token.toByteArray(Charsets.UTF_8)))

    fun extract(message: NdefMessage?): String? {
        for (record in message?.records.orEmpty()) {
            val text = when {
                record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                    String(record.type, Charsets.US_ASCII).equals(MIME_TYPE, ignoreCase = true) ->
                    String(record.payload, Charsets.UTF_8)
                record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) ->
                    decodeText(record.payload)
                else -> null
            }
            if (!text.isNullOrBlank() && text.length <= MAX_PAYLOAD) return text.trim()
        }
        return null
    }

    private fun decodeText(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt()
        val languageLength = status and 0x3F
        val charset = if ((status and 0x80) != 0) Charsets.UTF_16 else Charsets.UTF_8
        if (payload.size <= languageLength) return null
        return String(payload, 1 + languageLength, payload.size - 1 - languageLength, charset)
    }
}
