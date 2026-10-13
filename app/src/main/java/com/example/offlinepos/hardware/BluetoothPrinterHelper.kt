package com.example.offlinepos.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.offlinepos.ui.ReceiptData
import com.example.offlinepos.ui.StoreProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object BluetoothPrinterHelper {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    const val PRINTER_WIDTH_DOTS = 576 // 80mm standard: 576 dots (72 bytes)

    @SuppressLint("MissingPermission")
    fun getPairedDevices(context: Context): List<BluetoothDevice> {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Renders a complete high-fidelity 80mm thermal receipt in Myanmar language
     * onto a 576-pixel-wide Bitmap using native Android text rendering.
     */
    fun renderReceiptBitmap(receipt: ReceiptData, profile: StoreProfile): Bitmap {
        val width = PRINTER_WIDTH_DOTS
        // First pass: measure total height required
        val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val paintHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 21f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        val paintBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 21f
            typeface = Typeface.DEFAULT
        }
        val paintBodyBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paintTotal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Estimate height
        val baseHeaderHeight = 220
        val itemsHeight = (receipt.items.size * 55) + 60
        val summaryHeight = 260
        val footerLines = profile.footerMessage.split("\n").size
        val footerHeight = (footerLines * 30) + 120
        val totalHeight = baseHeaderHeight + itemsHeight + summaryHeight + footerHeight

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = 45f

        // 1. Store Name (Large Bold Centered)
        canvas.drawText(profile.storeName, width / 2f, y, paintTitle)
        y += 34f

        if (profile.headerNote.isNotBlank()) {
            canvas.drawText(profile.headerNote, width / 2f, y, paintHeader)
            y += 28f
        }
        if (profile.address.isNotBlank()) {
            canvas.drawText(profile.address, width / 2f, y, paintHeader)
            y += 28f
        }
        if (profile.phone.isNotBlank()) {
            canvas.drawText("ဖုန်း: ${profile.phone}", width / 2f, y, paintHeader)
            y += 32f
        }

        // Divider
        drawDashedLine(canvas, y, width)
        y += 26f

        // Bill Info
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(receipt.timestampEpochMs))
        canvas.drawText("ဘေလ်အမှတ်: #${receipt.saleId.take(8).uppercase()}", 20f, y, paintBody)
        y += 26f
        canvas.drawText("ရက်စွဲ: $dateStr", 20f, y, paintBody)
        canvas.drawText("အရောင်းဝန်ထမ်း: ${receipt.cashierId}", 320f, y, paintBody)
        y += 30f

        // Table Header
        drawDashedLine(canvas, y, width)
        y += 26f
        canvas.drawText("ပစ္စည်းအမည်", 20f, y, paintBodyBold)
        canvas.drawText("အရေအတွက်", 270f, y, paintBodyBold)
        canvas.drawText("နှုန်း", 390f, y, paintBodyBold)
        canvas.drawText("သင့်ငွေ", 490f, y, paintBodyBold)
        y += 26f
        drawDashedLine(canvas, y, width)
        y += 30f

        // Line Items
        receipt.items.forEach { line ->
            // Item name (left side)
            val nameDisplay = if (line.productNameSnapshot.length > 24) {
                line.productNameSnapshot.take(22) + ".."
            } else {
                line.productNameSnapshot
            }
            canvas.drawText(nameDisplay, 20f, y, paintBodyBold)
            y += 24f

            canvas.drawText("${line.quantity}", 280f, y, paintBody)
            canvas.drawText("%,d".format(line.unitPriceMinor), 380f, y, paintBody)
            canvas.drawText("%,d".format(line.lineTotalMinor), 480f, y, paintBodyBold)
            y += 30f
        }

        // Divider
        drawDashedLine(canvas, y, width)
        y += 30f

        // Summary Rows
        drawSummaryRow(canvas, "ကျသင့်ငွေ (Subtotal):", "%,d Ks".format(receipt.totals.subtotalMinor), y, paintBody)
        y += 28f

        if (receipt.totals.discountMinor > 0) {
            drawSummaryRow(canvas, "လျှော့စျေး (Discount):", "-%,d Ks".format(receipt.totals.discountMinor), y, paintBody)
            y += 28f
        }
        if (receipt.totals.taxMinor > 0) {
            val taxLabel = if (profile.taxRatePercent % 1.0 == 0.0) "${profile.taxRatePercent.toInt()}%" else "%.1f%%".format(profile.taxRatePercent)
            drawSummaryRow(canvas, "အခွန် (Tax $taxLabel):", "%,d Ks".format(receipt.totals.taxMinor), y, paintBody)
            y += 28f
        }

        drawSolidLine(canvas, y, width)
        y += 36f

        // Grand Total
        canvas.drawText("စုစုပေါင်း (TOTAL):", 20f, y, paintTotal)
        canvas.drawText("%,d Ks".format(receipt.totals.totalMinor), 320f, y, paintTotal)
        y += 38f

        drawSolidLine(canvas, y, width)
        y += 28f

        val paymentMethodBurmese = when (receipt.paymentMethod) {
            "CASH" -> "ငွေသား (Cash)"
            "QR_PAY" -> "KPay / Wave QR"
            "CARD" -> "ကတ် (Card)"
            else -> receipt.paymentMethod
        }
        drawSummaryRow(canvas, "ပေးချေမှုပုံစံ:", paymentMethodBurmese, y, paintBody)
        y += 28f
        drawSummaryRow(canvas, "ပေးငွေ (Tendered):", "%,d Ks".format(receipt.tenderedMinor), y, paintBody)
        y += 28f
        drawSummaryRow(canvas, "ပြန်အမ်းငွေ (Change):", "%,d Ks".format(receipt.changeMinor), y, paintBodyBold)
        y += 36f

        drawDashedLine(canvas, y, width)
        y += 30f

        // Footer
        profile.footerMessage.split("\n").forEach { line ->
            if (line.isNotBlank()) {
                canvas.drawText(line.trim(), width / 2f, y, paintHeader)
                y += 28f
            }
        }
        y += 10f
        canvas.drawText("*** ကျေးဇူးတင်ပါသည် ***", width / 2f, y, paintHeader)
        y += 40f

        // Trim bitmap to actual rendered height
        val finalHeight = y.toInt().coerceAtMost(totalHeight)
        return Bitmap.createBitmap(bitmap, 0, 0, width, finalHeight)
    }

    private fun drawDashedLine(canvas: Canvas, y: Float, width: Int) {
        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
        }
        var x = 10f
        while (x < width - 10) {
            canvas.drawLine(x, y, x + 8f, y, paint)
            x += 14f
        }
    }

    private fun drawSolidLine(canvas: Canvas, y: Float, width: Int) {
        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 3f
        }
        canvas.drawLine(10f, y, width - 10f, y, paint)
    }

    private fun drawSummaryRow(canvas: Canvas, label: String, value: String, y: Float, paint: Paint) {
        canvas.drawText(label, 20f, y, paint)
        canvas.drawText(value, 380f, y, paint)
    }

    /**
     * Converts an Android Bitmap into standard ESC/POS raster bit-image command data.
     * 80mm format: 576 dots wide = 72 bytes per row.
     */
    fun convertBitmapToEscPos(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8 // 72 bytes for 576 width

        val rawBits = ByteArray(widthBytes * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = (r * 299 + g * 587 + b * 114) / 1000

                // If pixel is dark (black ink)
                if (luminance < 165) {
                    val byteIndex = y * widthBytes + (x / 8)
                    val bitIndex = 7 - (x % 8)
                    rawBits[byteIndex] = (rawBits[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }
        }

        return EscPos.raster(widthBytes, height, rawBits)
    }

    /**
     * Connects to a paired Bluetooth thermal printer and prints the formatted Burmese receipt.
     */
    @SuppressLint("MissingPermission")
    suspend fun printReceipt(
        device: BluetoothDevice,
        receipt: ReceiptData,
        profile: StoreProfile,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null
        try {
            val bitmap = renderReceiptBitmap(receipt, profile)
            val rasterData = convertBitmapToEscPos(bitmap)

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            outputStream = socket.outputStream

            // Send Init, Print Bitmap, Feed & Cut Paper, Kick Cash Drawer
            outputStream.write(EscPos.INIT)
            outputStream.write(rasterData)
            outputStream.write(byteArrayOf(0x1B.toByte(), 0x64.toByte(), 0x03.toByte())) // Feed 3 lines
            outputStream.write(EscPos.CUT)
            outputStream.write(EscPos.CASH_DRAWER_KICK)
            outputStream.flush()

            Pair(true, "ဘေလ်ပြေစာကို ${device.name ?: "Printer"} သို့ အောင်မြင်စွာ ပရင့်ထုတ်ပြီးပါပြီ")
        } catch (e: Exception) {
            Pair(false, "Printer ချိတ်ဆက်မှု မအောင်မြင်ပါ: ${e.message}")
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
