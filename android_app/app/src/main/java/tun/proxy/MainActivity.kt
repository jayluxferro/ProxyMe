package tun.proxy

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import tun.proxy.service.Tun2SocksVpnService
import tun.proxy.service.Tun2SocksVpnService.Companion.ACTION_STOP_SERVICE
import tun.utils.Utils

class MainActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private var start: Button? = null
    private var stop: Button? = null
    private var hostEditText: EditText? = null
    private var statusHandler: Handler = Handler()
    private var utils: Utils? = null
    private var service: Tun2SocksVpnService? = null
    private val TAG = "${BuildConfig.APPLICATION_ID}->${this.javaClass.simpleName}"
    private val VPN_REQUEST_CODE = 100
    private val REQUEST_NOTIFICATION_PERMISSION = 1231
    private var intentVPNService: Intent? = null
    private val PREF_USER_CONFIG: String = "pref_user_config"
    private val PREF_FORMATTED_CONFIG: String = "pref_formatted_config"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        start = findViewById(R.id.start)
        stop = findViewById(R.id.stop)
        hostEditText = findViewById(R.id.host)

        start!!.setOnClickListener { startVpn(this, parseAndSaveHostPort()!!) }
        stop!!.setOnClickListener { stopVpn(this) }

        updateStatusView(st = true, stp = false)
        loadHostPort()

        utils = Utils(this)
        intentVPNService = Intent(this, Tun2SocksVpnService::class.java)
    }

    private fun updateStatusView(st: Boolean, stp: Boolean) {
        start!!.isEnabled = st
        start!!.visibility = if (st) View.VISIBLE else View.GONE

        stop!!.isEnabled = stp
        stop!!.visibility = if (stp) View.VISIBLE else View.GONE
        hostEditText!!.isEnabled = !stp
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val args = pref.extras
        val fragment =
            supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment)
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)
        supportFragmentManager.beginTransaction()
            .replace(R.id.activity_settings, fragment)
            .addToBackStack(null)
            .commit()
        title = pref.title
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

   override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.findItem(R.id.action_activity_settings)
        item.setEnabled(start!!.isEnabled)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
           R.id.action_activity_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }

            R.id.action_show_about -> AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name) + versionName)
                .setMessage(R.string.app_name)
                .show()

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    protected val versionName: String?
        get() {
            val packageManager = packageManager ?: return null

            return try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }


    val isRunning: Boolean
        get() = service != null && service!!.isRunning()


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "onActivityResult: User authorization succeeds, start VPN service")
                startService(intentVPNService)
            }
        }
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun loadHostPort() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val userConfig = prefs.getString(PREF_USER_CONFIG, "")

        if (TextUtils.isEmpty(userConfig)) {
            hostEditText!!.setText(
                String.format(
                    "%s:%s",
                    resources.getString(R.string.ip),
                    resources.getString(R.string.port)
                )
            )
            return
        }
        hostEditText!!.setText(userConfig)
    }

    private fun setHostPort() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this).edit()
        prefs.putString(PREF_USER_CONFIG, hostEditText!!.text.toString())
        prefs.putString(PREF_FORMATTED_CONFIG, parseAndSaveHostPort()!!)
        prefs.commit()
    }

    private fun parseAndSaveHostPort(): String? {
        val userConfig = hostEditText!!.text.toString()

        val regex = """(?:(socks5|http)://)?(?:(\w+):(\w+)@)?([\d.]+):(\d+)""".toRegex()
        val matchResult = regex.find(userConfig) ?: throw IllegalArgumentException("Invalid proxy format")

        val (proxyType, proxyUser, proxyPass, proxyHost, proxyPort) = matchResult.destructured

        val proxyData = ProxyData(
            proxyType = if (proxyType.isNotEmpty()) proxyType else "http",
            proxyUser = proxyUser.takeIf { it.isNotEmpty() },
            proxyPass = proxyPass.takeIf { it.isNotEmpty() },
            proxyHost = proxyHost,
            proxyPort = proxyPort.toInt()
        )

        return buildString {
            append("${proxyData.proxyType}://")
            if (proxyData.proxyUser != null && proxyData.proxyPass != null) {
                append("${proxyData.proxyUser}:${proxyData.proxyPass}@")
            }
            append("${proxyData.proxyHost}:${proxyData.proxyPort}")
        }
    }

    companion object {
        const val REQUEST_VPN: Int = 1
        const val REQUEST_CERT: Int = 2
    }

    private fun startVpn(context: Context, proxy: String) {
        Log.d(TAG, "startVpn: $proxy")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                this,
                POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }

        intentVPNService?.putExtra("data", proxy)
        val intent = VpnService.prepare(context)
        if (intent != null) {
            this.startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            context.startService(intentVPNService)
        }

        updateStatusView(st = false, stp = true)
        setHostPort()
    }

    private fun stopVpn(context: Context) {
        val intent = Intent(context, Tun2SocksVpnService::class.java)
        intent.setAction(ACTION_STOP_SERVICE)
        context.startService(intent)
        val result = context.stopService(intent)
        Log.d(TAG, "stopService: state:$result")
        updateStatusView(st = true, stp = false)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "This app requires notification permission", Toast.LENGTH_SHORT).show()
                startNotificationSetting()
            }
        }
    }

    private fun startNotificationSetting() {
        val applicationInfo = applicationInfo
        try {
            val intent = Intent()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
            intent.putExtra("app_package", applicationInfo.packageName)
            intent.putExtra("android.provider.extra.APP_PACKAGE", applicationInfo.packageName)
            intent.putExtra("app_uid", applicationInfo.uid)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            intent.data = Uri.fromParts("package", applicationInfo.packageName, null)
            startActivity(intent)
        }
    }
}

data class ProxyData(
    val proxyType: String = "http",
    val proxyUser: String? = null,
    val proxyPass: String? = null,
    val proxyHost: String,
    val proxyPort: Int
)