package mx.edu.utez.wallet.sensors.nfc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.provider.Settings

enum class NfcStatus { UNSUPPORTED, DISABLED, ENABLED }

/** NFC no pide permiso en ejecución (basta android.permission.NFC en el manifest); solo puede estar apagado. */
object NfcPermissionsHandler {
    fun status(context: Context): NfcStatus {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcStatus.UNSUPPORTED
        return if (adapter.isEnabled) NfcStatus.ENABLED else NfcStatus.DISABLED
    }

    fun isHceSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)

    fun settingsIntent(): Intent = Intent(Settings.ACTION_NFC_SETTINGS)
}
