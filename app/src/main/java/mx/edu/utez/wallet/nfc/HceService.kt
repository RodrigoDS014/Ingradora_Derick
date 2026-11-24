
package mx.edu.utez.wallet.sensors.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Servicio HCE que entrega el pase previamente armado.
 *
 * No permite READ BINARY antes de seleccionar correctamente el AID.
 */
class HceService : HostApduService() {

    @Volatile
    private var aidSelected = false

    override fun processCommandApdu(
        commandApdu: ByteArray?,
        extras: Bundle?
    ): ByteArray {

        if (commandApdu == null || commandApdu.size < 4) {
            aidSelected = false
            return HcePassProtocol.SW_WRONG_LENGTH
        }

        val command = commandApdu

        val isSelect =
            command.size >= 2 &&
                    command[1] == 0xA4.toByte()

        if (isSelect) {

            aidSelected = false

            val response = HcePassProtocol.respond(
                command,
                HcePassStore.current()
            )

            if (HcePassProtocol.isOk(response)) {
                aidSelected = true
            }

            return response
        }

        if (!aidSelected) {
            return HcePassProtocol.SW_NOT_ALLOWED
        }

        return HcePassProtocol.respond(
            command,
            HcePassStore.current()
        )
    }

    override fun onDeactivated(reason: Int) {
        aidSelected = false
    }
}
