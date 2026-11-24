
package mx.edu.utez.wallet.sensors

import android.content.Context
import mx.edu.utez.wallet.sensors.nfc.NfcPermissionsHandler
import mx.edu.utez.wallet.sensors.nfc.NfcReaderManager
import mx.edu.utez.wallet.sensors.nfc.NfcWriterManager
import mx.edu.utez.wallet.sensors.nfc.NfcStatus

/**
 * Punto de acceso a los módulos de sensores NFC.
 */
class SensorModule(context: Context) {

    private val appContext = context.applicationContext

    val nfcReader = NfcReaderManager()

    val nfcWriter = NfcWriterManager()

    fun nfcStatus(): NfcStatus {
        return NfcPermissionsHandler.status(appContext)
    }
}
