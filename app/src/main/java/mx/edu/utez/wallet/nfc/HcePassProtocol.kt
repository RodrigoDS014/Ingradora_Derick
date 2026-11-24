
package mx.edu.utez.wallet.sensors.nfc

/**
 * Protocolo APDU para transferencia de pases mediante HCE.
 *
 * SELECT AID: devuelve longitud del token + 9000.
 * READ BINARY: devuelve fragmentos del token + 9000.
 */
object HcePassProtocol {

    val AID = byteArrayOf(
        0xF0.toByte(), 0x55, 0x54, 0x45, 0x5A, 0x57, 0x01
    )

    const val CHUNK = 200
    const val MAX_TOKEN = 2048

    val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    val SW_NOT_ALLOWED = byteArrayOf(0x69, 0x85.toByte())
    val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
    val SW_WRONG_P1P2 = byteArrayOf(0x6B, 0x00)
    val SW_INS_UNSUPPORTED = byteArrayOf(0x6D, 0x00)
    val SW_CLA_UNSUPPORTED = byteArrayOf(0x6E, 0x00)
    val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)

    fun selectCommand(): ByteArray =
        byteArrayOf(
            0x00,
            0xA4.toByte(),
            0x04,
            0x00,
            AID.size.toByte()
        ) + AID + byteArrayOf(0x00)

    fun readCommand(offset: Int, length: Int): ByteArray {
        require(offset in 0..0xFFFF)
        require(length in 1..CHUNK)

        return byteArrayOf(
            0x00,
            0xB0.toByte(),
            (offset shr 8).toByte(),
            offset.toByte(),
            length.toByte()
        )
    }

    fun isOk(response: ByteArray): Boolean =
        response.size >= 2 &&
                response[response.size - 2] == 0x90.toByte() &&
                response[response.size - 1] == 0x00.toByte()

    fun respond(command: ByteArray, token: ByteArray?): ByteArray {

        if (command.size < 4) return SW_WRONG_LENGTH

        if (command[0] != 0x00.toByte()) {
            return SW_CLA_UNSUPPORTED
        }

        return when (command[1]) {

            // SELECT AID
            0xA4.toByte() -> {

                if (command.size < 5) return SW_WRONG_LENGTH

                if (command[2] != 0x04.toByte()) {
                    return SW_FILE_NOT_FOUND
                }

                if (command[3] != 0x00.toByte()) {
                    return SW_FILE_NOT_FOUND
                }

                val lc = command[4].toInt() and 0xFF

                if (lc != AID.size ||
                    (command.size != 5 + lc &&
                            command.size != 6 + lc)
                ) {
                    return SW_WRONG_LENGTH
                }

                if (!command.copyOfRange(5, 5 + lc).contentEquals(AID)) {
                    return SW_FILE_NOT_FOUND
                }

                if (token == null ||
                    token.isEmpty() ||
                    token.size > MAX_TOKEN
                ) {
                    return SW_NOT_ALLOWED
                }

                byteArrayOf(
                    (token.size shr 8).toByte(),
                    token.size.toByte()
                ) + SW_OK
            }

            // READ BINARY
            0xB0.toByte() -> {

                if (command.size != 5) {
                    return SW_WRONG_LENGTH
                }

                if (token == null ||
                    token.isEmpty() ||
                    token.size > MAX_TOKEN
                ) {
                    return SW_NOT_ALLOWED
                }

                val offset =
                    ((command[2].toInt() and 0xFF) shl 8) or
                            (command[3].toInt() and 0xFF)

                if (offset >= token.size) {
                    return SW_WRONG_P1P2
                }

                val le = command[4].toInt() and 0xFF

                val want = if (le == 0) {
                    CHUNK
                } else {
                    minOf(le, CHUNK)
                }

                val end = minOf(token.size, offset + want)

                token.copyOfRange(offset, end) + SW_OK
            }

            else -> SW_INS_UNSUPPORTED
        }
    }

    /**
     * Lado lector: selecciona el AID y recupera el token por fragmentos.
     */
    fun readToken(
        transceive: (ByteArray) -> ByteArray
    ): ByteArray? {

        return try {

            val selected = transceive(selectCommand())

            if (!isOk(selected) || selected.size != 4) {
                return null
            }

            val total =
                ((selected[0].toInt() and 0xFF) shl 8) or
                        (selected[1].toInt() and 0xFF)

            if (total <= 0 || total > MAX_TOKEN) {
                return null
            }

            val output = ByteArray(total)
            var offset = 0

            while (offset < total) {

                val requested = minOf(CHUNK, total - offset)

                val response = transceive(
                    readCommand(offset, requested)
                )

                if (!isOk(response)) {
                    return null
                }

                val received = response.size - 2

                if (received <= 0 || received > requested) {
                    return null
                }

                if (received > total - offset) {
                    return null
                }

                System.arraycopy(
                    response,
                    0,
                    output,
                    offset,
                    received
                )

                offset += received
            }

            output

        } catch (_: Exception) {
            null
        }
    }
}
