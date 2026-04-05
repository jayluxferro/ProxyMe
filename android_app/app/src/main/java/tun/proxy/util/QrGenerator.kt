package tun.proxy.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import tun.proxy.model.ProxyConfig

object QrGenerator {

    fun configToUri(config: ProxyConfig): String {
        val json = Gson().toJson(config)
        val encoded = Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        return "proxyme://import?data=$encoded"
    }

    fun uriToConfig(uri: String): ProxyConfig? {
        return try {
            val data = uri.substringAfter("data=", "")
            if (data.isEmpty()) return null
            val json = String(Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP))
            Gson().fromJson(json, ProxyConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
