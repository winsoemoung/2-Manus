package com.example.offlinepos.hardware

object EscPos {
    val INIT: ByteArray = byteArrayOf(0x1B.toByte(), 0x40.toByte()) // ESC @
    val CUT: ByteArray = byteArrayOf(0x1D.toByte(), 0x56.toByte(), 0x42.toByte(), 0x00.toByte()) // GS V B 0
    val CASH_DRAWER_KICK: ByteArray = byteArrayOf(0x1B.toByte(), 0x70.toByte(), 0x00.toByte(), 0x19.toByte(), 0xFA.toByte())

    fun raster(bitmapWidthBytes: Int, bitmapHeightDots: Int, data: ByteArray): ByteArray {
        require(data.size == bitmapWidthBytes * bitmapHeightDots) {
            "Bitmap size mismatch"
        }
        val header = byteArrayOf(
            0x1D.toByte(), 0x76.toByte(), 0x30.toByte(), 0x00.toByte(),
            (bitmapWidthBytes and 0xFF).toByte(),
            ((bitmapWidthBytes shr 8) and 0xFF).toByte(),
            (bitmapHeightDots and 0xFF).toByte(),
            ((bitmapHeightDots shr 8) and 0xFF).toByte(),
        )
        return header + data
    }
}
