
package mx.edu.utez.wallet.sensors.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import mx.edu.utez.wallet.sensors.shared.models.SensorError
import mx.edu.utez.wallet.sensors.shared.models.SensorResult

/**
 * Escribe pases UTEZ como mensajes NDEF.
 */
class NfcWriterManager {

    private val main = Handler(Looper.getMainLooper())

    fun start(
        activity: Activity,
        payload: String,
        onResult: (SensorResult<Unit>) -> Unit
    ): SensorResult<Unit> {

        if (payload.isBlank() ||
            payload.length > NdefPassCodec.MAX_PAYLOAD
        ) {
            return SensorResult.Failure(
                SensorError.INVALID_PAYLOAD,
                "El contenido a escribir no es válido"
            )
        }

        val adapter = NfcAdapter.getDefaultAdapter(activity)
            ?: return SensorResult.Failure(
                SensorError.UNSUPPORTED,
                "Este dispositivo no tiene NFC"
            )

        if (!adapter.isEnabled) {
            return SensorResult.Failure(
                SensorError.DISABLED,
                "NFC está apagado. Actívalo en los ajustes"
            )
        }

        val message = NdefPassCodec.build(payload)
        val delivered = AtomicBoolean(false)

        val flags =
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V

        return try {

            adapter.enableReaderMode(
                activity,
                { tag ->

                    if (delivered.compareAndSet(false, true)) {

                        val result = write(tag, message)

                        main.post {
                            onResult(result)
                        }
                    }
                },
                flags,
                null
            )

            SensorResult.Success(Unit)

        } catch (_: Exception) {

            SensorResult.Failure(
                SensorError.IO,
                "No se pudo iniciar el escritor NFC"
            )
        }
    }

    fun stop(activity: Activity) {
        runCatching {
            NfcAdapter.getDefaultAdapter(activity)
                ?.disableReaderMode(activity)
        }
    }

    private fun write(
        tag: Tag,
        message: NdefMessage
    ): SensorResult<Unit> {

        val size = message.toByteArray().size

        try {

            val ndef = Ndef.get(tag)

            if (ndef != null) {

                ndef.connect()

                try {

                    if (!ndef.isWritable) {
                        return SensorResult.Failure(
                            SensorError.TAG_READ_ONLY,
                            "La etiqueta es de solo lectura"
                        )
                    }

                    if (ndef.maxSize < size) {
                        return SensorResult.Failure(
                            SensorError.TAG_TOO_SMALL,
                            "La etiqueta no tiene espacio suficiente"
                        )
                    }

                    ndef.writeNdefMessage(message)

                    return SensorResult.Success(Unit)

                } finally {

                    runCatching { ndef.close() }
                }
            }

            val formatable = NdefFormatable.get(tag)
                ?: return SensorResult.Failure(
                    SensorError.UNSUPPORTED,
                    "Este tipo de etiqueta no es compatible"
                )

            formatable.connect()

            try {

                formatable.format(message)

                return SensorResult.Success(Unit)

            } finally {

                runCatching { formatable.close() }
            }

        } catch (e: TagLostException) {

            return SensorResult.Failure(
                SensorError.IO,
                "Se perdió el contacto con la etiqueta"
            )

        } catch (_: Exception) {

            return SensorResult.Failure(
                SensorError.IO,
                "No se pudo escribir la etiqueta"
            )
        }
    }
}
