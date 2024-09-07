package tun.utils

import android.content.Context
import android.content.pm.ApplicationInfo.FLAG_SYSTEM
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.Log
import tun.proxy.BuildConfig

class Utils(private val context: Context) {
    private val TAG = "${BuildConfig.APPLICATION_ID}->${this.javaClass.simpleName} "
    val sharedPreferences = context.getSharedPreferences("vpnconfig", Context.MODE_PRIVATE)

    init {
        Log.d(TAG, ": Utils init !")
    }

    private val PackageInfo.isSystemApp: Boolean
        get() = applicationInfo.flags and FLAG_SYSTEM != 0

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val bitmapWidth = drawable.intrinsicWidth
        val bitmapHeight = drawable.intrinsicHeight
        val bitmapConfig =
            if (drawable.opacity != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, bitmapConfig)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    public fun setVpnStatus(status: Boolean){
        val edit = sharedPreferences.edit()
        edit.putBoolean("vpnStatus", status)
        edit.commit()
    }
    public fun getVpnStatus(): Boolean{
        return sharedPreferences.getBoolean("vpnStatus", false)
    }
    public fun setProxyName(name: String){
        val edit = sharedPreferences.edit()
        edit.putString("proxyName", name)
        edit.commit()
    }
}

