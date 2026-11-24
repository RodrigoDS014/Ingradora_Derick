
package mx.edu.utez.wallet.sensors.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import mx.edu.utez.wallet.sensors.shared.models.SensorError
import mx.edu.utez.wallet.sensors.shared.models.SensorResult

/**
 * Lector NFC para etiquetas NDEF y dispositivos HCE.
 */
class NfcReaderManager {

    private val main = Handler(Looper.getMainLooper())

    fun start(
        activity: Activity,
        onResult: (SensorResult<String>) -> Unit
    ): SensorResult<Unit> {

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

                        val result = readTag(tag)

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
                "No se pudo iniciar el lector NFC"
            )
        }
    }

    fun stop(activity: Activity) {
        runCatching {
            NfcAdapter.getDefaultAdapter(activity)
                ?.disableReaderMode(activity)
        }
    }

    private fun readTag(tag: Tag): SensorResult<String> {

        return try {

            val ndef = Ndef.get(tag)

            if (ndef != null) {
                return readNdef(ndef)
            }

            val isoDep = IsoDep.get(tag)

            if (isoDep != null) {
                return readIsoDep(isoDep)
            }

            SensorResult.Failure(
                SensorError.UNSUPPORTED,
                "Este tipo de tarjeta no es compatible"
            )

        } catch (e: TagLostException) {

            SensorResult.Failure(
                SensorError.IO,
                "Se perdió el contacto con la etiqueta"
            )

        } catch (_: Exception) {

            SensorResult.Failure(
                SensorError.IO,
                "No se pudo identificar la etiqueta NFC"
            )
        }
    }

    private fun readNdef(ndef: Ndef): SensorResult<String> {

        return try {

            ndef.connect()

            val message =
                ndef.ndefMessage ?: ndef.cachedNdefMessage

            val token = NdefPassCodec.extract(message)

            if (token.isNullOrBlank()) {

                SensorResult.Failure(
                    SensorError.INVALID_PAYLOAD,
                    "La etiqueta está vacía o no contiene un pase válido"
                )

            } else {

                SensorResult.Success(token)
            }

        } catch (e: TagLostException) {

            SensorResult.Failure(
                SensorError.IO,
                "Se perdió el contacto. Mantén la tarjeta quieta"
            )

        } catch (_: Exception) {

            SensorResult.Failure(
                SensorError.IO,
                "No se pudo leer la etiqueta NDEF"
            )

        } finally {

            runCatching { ndef.close() }
        }
    }

    private fun readIsoDep(iso: IsoDep): SensorResult<String> {

        return try {

            iso.connect()
            iso.timeout = 5000

            val bytes = HcePassProtocol.readToken {
                iso.transceive(it)
            }

            if (bytes == null) {

                SensorResult.Failure(
                    SensorError.INVALID_PAYLOAD,
                    "El otro dispositivo no tiene un pase disponible o ya venció"
                )

            } else {

                val token = String(bytes, Charsets.UTF_8)

                if (token.isBlank()) {
                    SensorResult.Failure(
                        SensorError.INVALID_PAYLOAD,
                        "El pase recibido está vacío"
                    )
                } else {
                    SensorResult.Success(token)
                }
            }

        } catch (e: TagLostException) {

            SensorResult.Failure(
                SensorError.IO,
                "Se perdió el contacto entre los dispositivos"
            )

        } catch (_: Exception) {

            SensorResult.Failure(
                SensorError.IO,
                "No se pudo leer el dispositivo HCE"
            )

        } finally {

            runCatching { iso.close() }
        }
    }
}
