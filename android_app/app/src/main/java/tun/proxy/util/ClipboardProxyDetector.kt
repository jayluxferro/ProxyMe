package tun.proxy.util

import android.content.ClipboardManager
import android.content.Context

class ClipboardProxyDetector(context: Context) {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val proxyPattern = """(?:(?:socks5|http)://)?(?:\w+:\w+@)?[\w.\-]+:\d{1,5}""".toRegex()
    private var lastDetected: String? = null

    fun checkClipboard(): String? {
        val clip = clipboardManager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).text?.toString() ?: return null
        val match = proxyPattern.find(text.trim())?.value ?: return null

        // Don't re-detect the same text
        if (match == lastDetected) return null
        lastDetected = match
        return match
    }

    fun dismiss(text: String) {
        lastDetected = text
    }
}
