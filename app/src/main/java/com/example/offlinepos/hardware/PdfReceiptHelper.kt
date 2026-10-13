package com.example.offlinepos.hardware

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.offlinepos.ui.ReceiptData
import com.example.offlinepos.ui.StoreProfile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReceiptHelper {

    fun generateAndSharePdf(
        context: Context,
        receipt: ReceiptData,
        storeProfile: StoreProfile,
    ): Uri? {
        val pdfDocument = PdfDocument()
        val pageWidth = 450
        val baseHeight = 600
        val itemHeight = 35
        val pageHeight = baseHeight + (receipt.items.size * itemHeight)

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }

        // Draw background
        canvas.drawColor(Color.WHITE)

        var y = 45f

        // 1. Store Header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(storeProfile.storeName, pageWidth / 2f, y, paint)
        y += 22f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 12f
        if (storeProfile.address.isNotBlank()) {
            canvas.drawText(storeProfile.address, pageWidth / 2f, y, paint)
            y += 18f
        }
        if (storeProfile.phone.isNotBlank()) {
            canvas.drawText("ဖုန်း: ${storeProfile.phone}", pageWidth / 2f, y, paint)
            y += 18f
        }
        if (storeProfile.headerNote.isNotBlank()) {
            canvas.drawText(storeProfile.headerNote, pageWidth / 2f, y, paint)
            y += 18f
        }

        y += 10f
        paint.strokeWidth = 1.5f
        canvas.drawLine(25f, y, pageWidth - 25f, y, paint)
        y += 20f

        // 2. Receipt metadata
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 11f
        canvas.drawText("ပြေစာအမှတ်: ${receipt.saleId.take(16)}", 30f, y, paint)
        y += 18f
        canvas.drawText("ရက်စွဲ: ${receipt.businessDate}", 30f, y, paint)
        y += 18f
        canvas.drawText("ငွေကိုင်: ${receipt.cashierId}", 30f, y, paint)

        if (!receipt.customerName.isNullOrBlank()) {
            y += 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("ဝယ်သူအမည်: ${receipt.customerName}", 30f, y, paint)
            paint.typeface = Typeface.DEFAULT
        }

        y += 15f
        paint.strokeWidth = 1f
        canvas.drawLine(25f, y, pageWidth - 25f, y, paint)
        y += 18f

        // 3. Table Header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        canvas.drawText("ပစ္စည်းအမည်", 30f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("အရေအတွက်", 280f, y, paint)
        canvas.drawText("ကျသင့်ငွေ (Ks)", pageWidth - 30f, y, paint)
        y += 12f
        canvas.drawLine(25f, y, pageWidth - 25f, y, paint)
        y += 20f

        // 4. Line Items
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 12f
        receipt.items.forEachIndexed { index, item ->
            paint.textAlign = Paint.Align.LEFT
            val itemName = if (item.productNameSnapshot.length > 22) item.productNameSnapshot.take(20) + ".." else item.productNameSnapshot
            canvas.drawText("${index + 1}. $itemName", 30f, y, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${item.quantity} ${item.unitName}", 280f, y, paint)
            val lineTotal = (item.unitPriceMinor * item.quantity) - item.lineDiscountMinor
            canvas.drawText("%,d".format(lineTotal), pageWidth - 30f, y, paint)

            y += 16f
            if (item.lineDiscountMinor > 0) {
                paint.textSize = 10f
                paint.color = Color.DKGRAY
                canvas.drawText("(လျှော့ငွေ: -%,d Ks)".format(item.lineDiscountMinor), pageWidth - 30f, y, paint)
                paint.color = Color.BLACK
                paint.textSize = 12f
                y += 14f
            } else {
                y += 6f
            }
        }

        y += 6f
        canvas.drawLine(25f, y, pageWidth - 25f, y, paint)
        y += 22f

        // 5. Totals breakdown
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("စုစုပေါင်း ကုန်ကျငွေ:", 160f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("%,d Ks".format(receipt.subtotalMinor), pageWidth - 30f, y, paint)
        y += 18f

        if (receipt.discountMinor > 0) {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("အထူးလျှော့ငွေ:", 160f, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-%,d Ks".format(receipt.discountMinor), pageWidth - 30f, y, paint)
            y += 18f
        }

        if (receipt.taxMinor > 0) {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("အခွန် (${storeProfile.taxRatePercent}%):", 160f, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("%,d Ks".format(receipt.taxMinor), pageWidth - 30f, y, paint)
            y += 18f
        }

        y += 6f
        paint.strokeWidth = 1.5f
        canvas.drawLine(150f, y, pageWidth - 25f, y, paint)
        y += 22f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("ကျသင့်ငွေ စုစုပေါင်း:", 160f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("%,d Ks".format(receipt.totalMinor), pageWidth - 30f, y, paint)
        y += 25f

        // Payment info
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("ငွေပေးချေမှုပုံစံ: ${receipt.paymentMethod}", 160f, y, paint)
        y += 18f

        if (receipt.tenderedMinor > 0 && receipt.paymentMethod.contains("CASH", ignoreCase = true)) {
            canvas.drawText("ပေးငွေ: %,d Ks".format(receipt.tenderedMinor), 160f, y, paint)
            y += 18f
            canvas.drawText("ပြန်အမ်းငွေ: %,d Ks".format(receipt.changeMinor), 160f, y, paint)
            y += 22f
        }

        // Draw QR code if possible
        val qrBitmap = QrCodeHelper.generateQrBitmap("787-POS:${receipt.saleId}:${receipt.totalMinor}Ks", 130)
        if (qrBitmap != null) {
            val qrLeft = (pageWidth - 130) / 2f
            canvas.drawBitmap(qrBitmap, qrLeft, y, paint)
            y += 140f
        }

        // Footer message
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f
        val footer = storeProfile.footerMessage.ifBlank { "ဝယ်ယူအားပေးမှုကို အထူးပင် ကျေးဇူးတင်ရှိပါသည်" }
        canvas.drawText(footer, pageWidth / 2f, y, paint)

        pdfDocument.finishPage(page)

        // Save to cache dir
        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val pdfFile = File(receiptsDir, "Receipt_${receipt.saleId.take(8)}_${System.currentTimeMillis()}.pdf")

        return try {
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, pdfFile)

            // Trigger Share Intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "ပြေစာ: ${storeProfile.storeName}")
                putExtra(Intent.EXTRA_TEXT, "${storeProfile.storeName} မှ အရောင်းပြေစာ #${receipt.saleId.take(8)} (ကျသင့်ငွေ: %,d Ks)".format(receipt.totalMinor))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "ပြေစာ PDF မျှဝေမည် (Share / Print)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            contentUri
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }
}
