package com.radio.player.util

import android.content.Context
import android.graphics.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {

    fun generateBitmap(content: String, sizePx: Int = 512, foreground: Int = Color.BLACK, background: Int = Color.WHITE): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)

        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) foreground else background)
            }
        }
        return bitmap
    }
}