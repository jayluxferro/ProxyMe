package tun.proxy

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import tun.proxy.adapter.ConnectionLogAdapter
import tun.proxy.adapter.SavedConfigsAdapter
import tun.proxy.model.ProxyConfig
import tun.proxy.model.ProxyData
import tun.proxy.model.ConnectionEvent
import tun.proxy.repository.ConfigRepository
import tun.proxy.repository.ConnectionLogRepository
import android.widget.ArrayAdapter
import android.widget.Spinner
import tun.proxy.adapter.RotationConfigAdapter
import tun.proxy.service.ProxyRotationManager
import tun.proxy.util.ClipboardProxyDetector
import tun.proxy.util.ConfigImporter
import tun.proxy.util.ProxyHealthCheck
import tun.proxy.util.QrGenerator
import tun.proxy.service.ACTION_VPN_STATE_CHANGED
import tun.proxy.service.EXTRA_ERROR_MESSAGE
import tun.proxy.service.EXTRA_VPN_STATE
import tun.proxy.service.Tun2SocksVpnService
import tun.proxy.service.Tun2SocksVpnService.Companion.ACTION_STOP_SERVICE
import tun.proxy.service.VpnState
import tun.utils.Utils
import java.util.UUID

class MainActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var hostEditText: TextInputEditText
    private lateinit var hostLayout: TextInputLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var statusProxy: TextView
    private lateinit var statusProgress: CircularProgressIndicator
    private lateinit var btnSave: MaterialButton
    private lateinit var btnLoad: MaterialButton
    private lateinit var containerView: View

    private lateinit var utils: Utils
    private lateinit var configRepository: ConfigRepository
    private lateinit var logRepository: ConnectionLogRepository
    private var connectStartTime: Long = 0
    private val TAG = "MainActivity"
    private val VPN_REQUEST_CODE = 100
    private val REQUEST_NOTIFICATION_PERMISSION = 1231
    private var intentVPNService: Intent? = null
    private val PREF_USER_CONFIG = "pref_user_config"
    private val PREF_FORMATTED_CONFIG = "pref_formatted_config"

    private var currentVpnState = VpnState.DISCONNECTED
    private var pendingProxy: String? = null
    private lateinit var clipboardDetector: ClipboardProxyDetector
    private lateinit var rotationManager: ProxyRotationManager
    private var lastDeletedConfig: ProxyConfig? = null

    // Saved configs bottom sheet reference (to dismiss when opening add/edit)
    private var savedConfigsSheet: BottomSheetDialog? = null

    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stateName = intent.getStringExtra(EXTRA_VPN_STATE) ?: return
            val state = try { VpnState.valueOf(stateName) } catch (e: Exception) { return }
            val errorMsg = intent.getStringExtra(EXTRA_ERROR_MESSAGE)
            onVpnStateChanged(state, errorMsg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        containerView = findViewById(R.id.container)
        startButton = findViewById(R.id.start)
        stopButton = findViewById(R.id.stop)
        hostEditText = findViewById(R.id.host)
        hostLayout = findViewById(R.id.host_layout)
        statusDot = findViewById(R.id.status_dot)
        statusText = findViewById(R.id.status_text)
        statusProxy = findViewById(R.id.status_proxy)
        statusProgress = findViewById(R.id.status_progress)
        btnSave = findViewById(R.id.btn_save)
        btnLoad = findViewById(R.id.btn_load)

        configRepository = ConfigRepository(this)
        logRepository = ConnectionLogRepository(this)
        clipboardDetector = ClipboardProxyDetector(this)
        rotationManager = ProxyRotationManager(this)
        utils = Utils(this)
        intentVPNService = Intent(this, Tun2SocksVpnService::class.java)

        handleDeepLink(intent)

        startButton.setOnClickListener {
            val proxy = parseProxy()
            if (proxy != null) {
                startVpn(proxy)
            }
        }
        stopButton.setOnClickListener { confirmDisconnect() }
        btnSave.setOnClickListener { showAddEditConfigSheet() }
        btnLoad.setOnClickListener { showSavedConfigsBottomSheet() }

        if (savedInstanceState != null) {
            val savedHost = savedInstanceState.getString("host_text", "")
            hostEditText.setText(savedHost)
            val savedState = savedInstanceState.getString("vpn_state", VpnState.DISCONNECTED.name)
            currentVpnState = try { VpnState.valueOf(savedState) } catch (e: Exception) { VpnState.DISCONNECTED }
            applyVpnStateToUI(currentVpnState)
        } else {
            loadHostPort()
            applyVpnStateToUI(VpnState.DISCONNECTED)
            showOnboardingIfFirstRun()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("host_text", hostEditText.text?.toString() ?: "")
        outState.putString("vpn_state", currentVpnState.name)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ACTION_VPN_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(vpnStateReceiver, filter)
        }
        // Check clipboard for proxy URLs
        checkClipboardForProxy()

        // Sync UI with actual VPN state
        val isVpnActive = Tun2SocksVpnService.isActive
        if (isVpnActive && currentVpnState == VpnState.DISCONNECTED) {
            applyVpnStateToUI(VpnState.CONNECTED)
        } else if (!isVpnActive && currentVpnState != VpnState.DISCONNECTED) {
            applyVpnStateToUI(VpnState.DISCONNECTED)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(vpnStateReceiver)
    }

    private fun onVpnStateChanged(state: VpnState, errorMsg: String?) {
        currentVpnState = state
        applyVpnStateToUI(state)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val rawConfig = prefs.getString(PREF_FORMATTED_CONFIG, "") ?: ""
        val protocol = when {
            rawConfig.startsWith("socks5://") -> "socks5"
            else -> "http"
        }

        when (state) {
            VpnState.CONNECTED -> {
                connectStartTime = System.currentTimeMillis()
                logRepository.log(ConnectionEvent(System.currentTimeMillis(), "Connected", protocol))
            }
            VpnState.DISCONNECTED -> {
                val duration = if (connectStartTime > 0) System.currentTimeMillis() - connectStartTime else null
                connectStartTime = 0
                logRepository.log(ConnectionEvent(System.currentTimeMillis(), "Disconnected", protocol, duration = duration))
            }
            VpnState.FAILED -> {
                logRepository.log(ConnectionEvent(System.currentTimeMillis(), "Failed", protocol, error = errorMsg))
                Snackbar.make(containerView, getString(R.string.vpn_connection_failed, errorMsg ?: "Unknown error"), Snackbar.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    private fun applyVpnStateToUI(state: VpnState) {
        currentVpnState = state
        val dotDrawable = statusDot.background
        when (state) {
            VpnState.DISCONNECTED -> {
                startButton.visibility = View.VISIBLE
                stopButton.visibility = View.GONE
                hostEditText.isEnabled = true
                btnSave.isEnabled = true
                statusDot.visibility = View.VISIBLE
                statusProgress.visibility = View.GONE
                if (dotDrawable is GradientDrawable) {
                    dotDrawable.setColor(ContextCompat.getColor(this, R.color.colorError))
                }
                statusText.text = getString(R.string.status_disconnected)
                statusText.setTextColor(ContextCompat.getColor(this, R.color.colorOnSurface))
                statusProxy.visibility = View.GONE
            }
            VpnState.CONNECTING -> {
                startButton.visibility = View.GONE
                stopButton.visibility = View.GONE
                hostEditText.isEnabled = false
                btnSave.isEnabled = false
                statusDot.visibility = View.GONE
                statusProgress.visibility = View.VISIBLE
                statusText.text = getString(R.string.status_connecting)
                statusText.setTextColor(ContextCompat.getColor(this, R.color.colorWarning))
                statusProxy.visibility = View.GONE
            }
            VpnState.CONNECTED -> {
                startButton.visibility = View.GONE
                stopButton.visibility = View.VISIBLE
                hostEditText.isEnabled = false
                btnSave.isEnabled = false
                statusDot.visibility = View.VISIBLE
                statusProgress.visibility = View.GONE
                if (dotDrawable is GradientDrawable) {
                    dotDrawable.setColor(ContextCompat.getColor(this, R.color.colorSuccess))
                }
                statusText.text = getString(R.string.status_connected)
                statusText.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess))
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val rawConfig = prefs.getString(PREF_FORMATTED_CONFIG, "") ?: ""
                // Show only protocol type, not the address
                val proxyLabel = when {
                    rawConfig.startsWith("socks5://") -> "SOCKS5 Proxy"
                    rawConfig.startsWith("http://") -> "HTTP Proxy"
                    rawConfig.isNotEmpty() -> "Proxy"
                    else -> ""
                }
                // Show rotation status if active
                val rotState = rotationManager.getState()
                val label = if (rotState.enabled && rotState.configIds.size > 1) {
                    val idx = rotState.currentIndex + 1
                    getString(R.string.rotation_status_active, idx, rotState.configIds.size)
                } else {
                    proxyLabel
                }
                statusProxy.text = label
                statusProxy.visibility = if (label.isEmpty()) View.GONE else View.VISIBLE
            }
            VpnState.FAILED -> {
                startButton.visibility = View.VISIBLE
                stopButton.visibility = View.GONE
                hostEditText.isEnabled = true
                btnSave.isEnabled = true
                statusDot.visibility = View.VISIBLE
                statusProgress.visibility = View.GONE
                if (dotDrawable is GradientDrawable) {
                    dotDrawable.setColor(ContextCompat.getColor(this, R.color.colorError))
                }
                statusText.text = getString(R.string.status_disconnected)
                statusText.setTextColor(ContextCompat.getColor(this, R.color.colorOnSurface))
                statusProxy.visibility = View.GONE
            }
        }
    }

    private fun confirmDisconnect() {
        MaterialAlertDialogBuilder(this, R.style.Widget_ProxyMe_Dialog)
            .setTitle(R.string.disconnect_confirm_title)
            .setMessage(R.string.disconnect_confirm_message)
            .setPositiveButton(R.string.btn_disconnect) { _, _ -> stopVpn() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // --- Add/Edit Configuration Sheet ---

    fun showAddEditConfigSheet(config: ProxyConfig? = null, prefillFromInput: Boolean = false) {
        savedConfigsSheet?.dismiss()

        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_add_config, null)

        val title = view.findViewById<TextView>(R.id.sheet_title)
        val protocolToggle = view.findViewById<MaterialButtonToggleGroup>(R.id.protocol_toggle)
        val hostInput = view.findViewById<TextInputEditText>(R.id.input_host)
        val hostLayout = view.findViewById<TextInputLayout>(R.id.input_host_layout)
        val portInput = view.findViewById<TextInputEditText>(R.id.input_port)
        val portLayout = view.findViewById<TextInputLayout>(R.id.input_port_layout)
        val authSwitch = view.findViewById<SwitchMaterial>(R.id.auth_switch)
        val authFields = view.findViewById<View>(R.id.auth_fields)
        val usernameInput = view.findViewById<TextInputEditText>(R.id.input_username)
        val usernameLayout = view.findViewById<TextInputLayout>(R.id.input_username_layout)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.input_password)
        val passwordLayout = view.findViewById<TextInputLayout>(R.id.input_password_layout)
        val nameInput = view.findViewById<TextInputEditText>(R.id.input_name)
        val nameLayout = view.findViewById<TextInputLayout>(R.id.input_name_layout)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnSave = view.findViewById<MaterialButton>(R.id.btn_form_save)

        val isEdit = config != null
        title.text = getString(if (isEdit) R.string.edit_config_title else R.string.add_config_title)

        // Pre-fill from existing config
        if (config != null) {
            protocolToggle.check(if (config.protocol == "socks5") R.id.btn_socks5 else R.id.btn_http)
            hostInput.setText(config.host)
            portInput.setText(config.port.toString())
            authSwitch.isChecked = config.authEnabled
            authFields.visibility = if (config.authEnabled) View.VISIBLE else View.GONE
            usernameInput.setText(config.username ?: "")
            passwordInput.setText(config.password ?: "")
            nameInput.setText(config.name)
        } else if (prefillFromInput) {
            // Try to parse the current proxy input and pre-fill
            val parsed = parseProxyData()
            if (parsed != null) {
                protocolToggle.check(if (parsed.proxyType == "socks5") R.id.btn_socks5 else R.id.btn_http)
                hostInput.setText(parsed.proxyHost)
                portInput.setText(parsed.proxyPort.toString())
                if (!parsed.proxyUser.isNullOrEmpty()) {
                    authSwitch.isChecked = true
                    authFields.visibility = View.VISIBLE
                    usernameInput.setText(parsed.proxyUser)
                    passwordInput.setText(parsed.proxyPass ?: "")
                }
            }
        }

        authSwitch.setOnCheckedChangeListener { _, isChecked ->
            authFields.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                usernameInput.text?.clear()
                passwordInput.text?.clear()
                usernameLayout.error = null
                passwordLayout.error = null
            }
        }

        btnCancel.setOnClickListener { sheet.dismiss() }

        btnSave.setOnClickListener {
            // Clear previous errors
            hostLayout.error = null
            portLayout.error = null
            usernameLayout.error = null
            passwordLayout.error = null
            nameLayout.error = null

            val host = hostInput.text?.toString()?.trim() ?: ""
            val portStr = portInput.text?.toString()?.trim() ?: ""
            val name = nameInput.text?.toString()?.trim() ?: ""
            val protocol = if (protocolToggle.checkedButtonId == R.id.btn_socks5) "socks5" else "http"
            val authEnabled = authSwitch.isChecked
            val username = usernameInput.text?.toString()?.trim()
            val password = passwordInput.text?.toString()?.trim()

            // Validate
            var valid = true
            if (host.isEmpty()) {
                hostLayout.error = getString(R.string.error_host_required)
                valid = false
            } else if (!isValidHost(host)) {
                hostLayout.error = getString(R.string.error_host_invalid)
                valid = false
            }
            val port = portStr.toIntOrNull()
            if (portStr.isEmpty()) {
                portLayout.error = getString(R.string.error_port_required)
                valid = false
            } else if (port == null || port < 1 || port > 65535) {
                portLayout.error = getString(R.string.error_port_invalid)
                valid = false
            }
            if (authEnabled) {
                if (username.isNullOrEmpty()) {
                    usernameLayout.error = getString(R.string.error_username_required)
                    valid = false
                }
                if (password.isNullOrEmpty()) {
                    passwordLayout.error = getString(R.string.error_password_required)
                    valid = false
                }
            }
            if (name.isEmpty()) {
                nameLayout.error = getString(R.string.error_name_required)
                valid = false
            }
            if (!valid || port == null) return@setOnClickListener

            val newConfig = ProxyConfig(
                id = config?.id ?: UUID.randomUUID().toString(),
                name = name,
                protocol = protocol,
                host = host,
                port = port,
                authEnabled = authEnabled,
                username = if (authEnabled) username else null,
                password = if (authEnabled) password else null,
                createdAt = config?.createdAt ?: System.currentTimeMillis()
            )

            if (isEdit) {
                configRepository.update(newConfig)
                Snackbar.make(containerView, R.string.config_updated, Snackbar.LENGTH_SHORT).show()
            } else {
                configRepository.save(newConfig)
                Snackbar.make(containerView, R.string.config_saved, Snackbar.LENGTH_SHORT).show()
            }

            // Set the proxy address in the main input
            hostEditText.setText(newConfig.proxyAddress)
            this.hostLayout.error = null

            hideKeyboard()
            sheet.dismiss()
        }

        sheet.setContentView(view)
        sheet.show()
    }

    private fun isValidHost(host: String): Boolean {
        // Accept IPv4 or simple hostnames
        val ipv4 = """^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""".toRegex()
        val match = ipv4.matchEntire(host)
        if (match != null) {
            return match.groupValues.drop(1).all { it.toInt() in 0..255 }
        }
        // Accept hostnames (letters, digits, dots, hyphens)
        return host.matches("""^[a-zA-Z0-9]([a-zA-Z0-9\-.]*[a-zA-Z0-9])?$""".toRegex())
    }

    // --- Saved Configs Bottom Sheet ---

    private fun showSavedConfigsBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        savedConfigsSheet = bottomSheet
        val sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_saved_configs, null)

        val recyclerView = sheetView.findViewById<RecyclerView>(R.id.saved_configs_list)
        val emptyState = sheetView.findViewById<TextView>(R.id.empty_state)
        val btnAddNew = sheetView.findViewById<MaterialButton>(R.id.btn_add_new)

        val adapter = SavedConfigsAdapter(
            onUseClick = { config ->
                hostEditText.setText(config.proxyAddress)
                hostLayout.error = null
                bottomSheet.dismiss()
                Snackbar.make(containerView, getString(R.string.config_loaded, config.name), Snackbar.LENGTH_SHORT).show()
            },
            onEditClick = { config ->
                showAddEditConfigSheet(config = config)
            },
            onShareClick = { config ->
                showQrSheet(config)
            },
            onDeleteClick = { config ->
                lastDeletedConfig = config
                configRepository.delete(config.id)
                val updatedList = configRepository.getAll()
                (recyclerView.adapter as? SavedConfigsAdapter)?.submitList(updatedList)
                emptyState.visibility = if (updatedList.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (updatedList.isEmpty()) View.GONE else View.VISIBLE
                Snackbar.make(sheetView, R.string.config_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.btn_undo) {
                        lastDeletedConfig?.let { deleted ->
                            configRepository.save(deleted)
                            val restoredList = configRepository.getAll()
                            (recyclerView.adapter as? SavedConfigsAdapter)?.submitList(restoredList)
                            emptyState.visibility = if (restoredList.isEmpty()) View.VISIBLE else View.GONE
                            recyclerView.visibility = if (restoredList.isEmpty()) View.GONE else View.VISIBLE
                        }
                        lastDeletedConfig = null
                    }
                    .show()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val configs = configRepository.getAll()
        adapter.submitList(configs)
        emptyState.visibility = if (configs.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (configs.isEmpty()) View.GONE else View.VISIBLE

        btnAddNew.setOnClickListener {
            showAddEditConfigSheet()
        }

        bottomSheet.setContentView(sheetView)
        bottomSheet.show()
    }

    // --- Proxy Parsing ---

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(containerView.windowToken, 0)
    }

    private val proxyRegex = """(?:(socks5|http)://)?(?:(\w+):(\w+)@)?([\w.\-]+):(\d+)""".toRegex()

    private fun parseProxyData(): ProxyData? {
        val input = hostEditText.text?.trim()?.toString() ?: return null
        if (input.isEmpty()) return null
        val matchResult = proxyRegex.find(input) ?: return null
        val (proxyType, proxyUser, proxyPass, proxyHost, proxyPort) = matchResult.destructured
        val port = proxyPort.toIntOrNull() ?: return null
        return ProxyData(
            proxyType = if (proxyType.isNotEmpty()) proxyType else "http",
            proxyUser = proxyUser.takeIf { it.isNotEmpty() },
            proxyPass = proxyPass.takeIf { it.isNotEmpty() },
            proxyHost = proxyHost,
            proxyPort = port
        )
    }

    private fun parseProxy(): String? {
        val input = hostEditText.text?.trim()?.toString()
        if (input.isNullOrEmpty()) {
            hostLayout.error = getString(R.string.error_empty_input)
            return null
        }

        val matchResult = proxyRegex.find(input)
        if (matchResult == null) {
            hostLayout.error = getString(R.string.error_bad_format)
            return null
        }

        val (proxyType, proxyUser, proxyPass, proxyHost, proxyPort) = matchResult.destructured
        val port = proxyPort.toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            hostLayout.error = getString(R.string.error_port_range)
            return null
        }

        if (!isValidHost(proxyHost)) {
            hostLayout.error = getString(R.string.error_host_invalid)
            return null
        }

        hostLayout.error = null
        val data = ProxyData(
            proxyType = if (proxyType.isNotEmpty()) proxyType else "http",
            proxyUser = proxyUser.takeIf { it.isNotEmpty() },
            proxyPass = proxyPass.takeIf { it.isNotEmpty() },
            proxyHost = proxyHost,
            proxyPort = port
        )

        return buildString {
            append("${data.proxyType}://")
            if (data.proxyUser != null && data.proxyPass != null) {
                append("${data.proxyUser}:${data.proxyPass}@")
            }
            append("${data.proxyHost}:${data.proxyPort}")
        }
    }

    // --- VPN Control ---

    private fun startVpn(proxy: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                this, POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(POST_NOTIFICATIONS), REQUEST_NOTIFICATION_PERMISSION
            )
        }

        pendingProxy = proxy
        intentVPNService?.putExtra("data", proxy)
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            startService(intentVPNService)
        }

        // Store proxy details
        val maskedProxy = Tun2SocksVpnService.maskProxyUrl(proxy)
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(PREF_USER_CONFIG, hostEditText.text.toString())
            .putString(PREF_FORMATTED_CONFIG, maskedProxy)
            .apply()

        applyVpnStateToUI(VpnState.CONNECTING)
    }

    private fun stopVpn() {
        // Disable rotation if active, so it doesn't restart the VPN
        if (rotationManager.getState().enabled) {
            rotationManager.disable()
        }
        try {
            val intent = Intent(this, Tun2SocksVpnService::class.java)
            intent.action = ACTION_STOP_SERVICE
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "stopVpn: ${e.message}")
        }
        applyVpnStateToUI(VpnState.DISCONNECTED)
    }

    // --- Navigation & Menus ---

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat, pref: Preference
    ): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment)
        fragment.arguments = args
        @Suppress("DEPRECATION")
        fragment.setTargetFragment(caller, 0)
        supportFragmentManager.beginTransaction()
            .replace(R.id.activity_settings, fragment)
            .addToBackStack(null)
            .commit()
        title = pref.title
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.findItem(R.id.action_activity_settings)
        item.isEnabled = currentVpnState == VpnState.DISCONNECTED
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_rotation -> showRotationSettings()
            R.id.action_test_proxy -> testProxyConnection()
            R.id.action_connection_log -> showConnectionLog()
            R.id.action_import -> showImportDialog()
            R.id.action_activity_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.action_show_about -> showAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // --- QR Code Sharing ---

    private fun showQrSheet(config: ProxyConfig) {
        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_qr, null)

        view.findViewById<TextView>(R.id.qr_config_name).text = config.name
        val uri = QrGenerator.configToUri(config)
        val bitmap = QrGenerator.generateQrBitmap(uri)
        view.findViewById<ImageView>(R.id.qr_image).setImageBitmap(bitmap)

        view.findViewById<MaterialButton>(R.id.btn_share_qr).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ProxyMe Config: ${config.name}")
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.btn_share)))
        }

        sheet.setContentView(view)
        sheet.show()
    }

    // --- Proxy Health Check ---

    private fun testProxyConnection() {
        val parsed = parseProxyData()
        if (parsed == null) {
            hostLayout.error = getString(R.string.error_bad_format)
            return
        }
        hostLayout.error = null
        Snackbar.make(containerView, R.string.health_testing, Snackbar.LENGTH_SHORT).show()

        Thread {
            val result = ProxyHealthCheck.test(parsed.proxyHost, parsed.proxyPort)
            runOnUiThread {
                if (result.reachable) {
                    Snackbar.make(containerView, getString(R.string.health_success, result.latencyMs.toInt()), Snackbar.LENGTH_LONG).show()
                } else {
                    Snackbar.make(containerView, getString(R.string.health_failed, result.error ?: "Unknown"), Snackbar.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // --- Connection Log ---

    private fun showConnectionLog() {
        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_log, null)

        val recyclerView = view.findViewById<RecyclerView>(R.id.log_list)
        val emptyState = view.findViewById<TextView>(R.id.log_empty_state)
        val btnClear = view.findViewById<MaterialButton>(R.id.btn_clear_log)
        val btnExport = view.findViewById<MaterialButton>(R.id.btn_export_log)

        val adapter = ConnectionLogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val events = logRepository.getAll()
        adapter.submitList(events)
        emptyState.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE

        btnClear.setOnClickListener {
            logRepository.clear()
            adapter.submitList(emptyList())
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }

        btnExport.setOnClickListener {
            exportConnectionLog()
        }

        sheet.setContentView(view)
        sheet.show()
    }

    @SuppressLint("SimpleDateFormat")
    private fun exportConnectionLog() {
        val events = logRepository.getAll()
        if (events.isEmpty()) {
            Snackbar.make(containerView, R.string.export_empty, Snackbar.LENGTH_SHORT).show()
            return
        }

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val csv = StringBuilder()
        csv.appendLine("Timestamp,Action,Protocol,Duration (s),Error")
        for (event in events) {
            val time = dateFormat.format(java.util.Date(event.timestamp))
            val duration = event.duration?.let { it / 1000 }?.toString() ?: ""
            val error = event.error?.replace(",", ";")?.replace("\n", " ") ?: ""
            csv.appendLine("$time,${event.action},${event.protocol},$duration,$error")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_TEXT, csv.toString())
            putExtra(Intent.EXTRA_SUBJECT, "ProxyMe Connection Log")
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.btn_export)))
    }

    // --- Deep Link Import ---

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "proxyme" && uri.host == "import") {
            val config = QrGenerator.uriToConfig(uri.toString())
            if (config == null) {
                Snackbar.make(containerView, R.string.qr_import_failed, Snackbar.LENGTH_LONG).show()
                return
            }
            showAddEditConfigSheet(config = config.copy(id = UUID.randomUUID().toString()))
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    // --- Proxy Rotation ---

    private fun showRotationSettings() {
        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_rotation, null)

        val rotationSwitch = view.findViewById<SwitchMaterial>(R.id.rotation_switch)
        val settingsArea = view.findViewById<View>(R.id.rotation_settings)
        val intervalSpinner = view.findViewById<Spinner>(R.id.rotation_interval_spinner)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rotation_config_list)
        val emptyState = view.findViewById<TextView>(R.id.rotation_empty)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btn_add_to_rotation)
        val statusText = view.findViewById<TextView>(R.id.rotation_status)

        val state = rotationManager.getState()
        val rotationConfigs = rotationManager.getRotationConfigs().toMutableList()

        // Interval spinner
        val intervalLabels = resources.getStringArray(R.array.rotation_intervals)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervalLabels)
        intervalSpinner.adapter = spinnerAdapter
        val currentIntervalIndex = ProxyRotationManager.INTERVAL_OPTIONS.indexOf(state.intervalMinutes)
        if (currentIntervalIndex >= 0) intervalSpinner.setSelection(currentIntervalIndex)

        // Config list adapter
        val configAdapter = RotationConfigAdapter { config ->
            rotationConfigs.remove(config)
            configAdapter_refresh(configAdapter_ref = recyclerView, configs = rotationConfigs, emptyState = emptyState)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = configAdapter
        configAdapter.submitList(rotationConfigs)
        emptyState.visibility = if (rotationConfigs.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (rotationConfigs.isEmpty()) View.GONE else View.VISIBLE

        // Switch state
        rotationSwitch.isChecked = state.enabled
        settingsArea.alpha = if (state.enabled) 1f else 0.5f

        // Show status if active
        if (state.enabled && state.nextRotationTime > System.currentTimeMillis()) {
            val remainingMs = state.nextRotationTime - System.currentTimeMillis()
            val remainingMin = (remainingMs / 60_000).toInt()
            statusText.text = getString(R.string.rotation_next_in,
                if (remainingMin > 0) "${remainingMin}m" else "<1m"
            )
            statusText.visibility = View.VISIBLE
        }

        rotationSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsArea.alpha = if (isChecked) 1f else 0.5f
            if (isChecked) {
                val ids = (recyclerView.adapter as? RotationConfigAdapter)?.getConfigIds() ?: emptyList()
                if (ids.size < 2) {
                    rotationSwitch.isChecked = false
                    Snackbar.make(view, R.string.rotation_need_configs, Snackbar.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }
                val intervalIndex = intervalSpinner.selectedItemPosition.coerceIn(0, ProxyRotationManager.INTERVAL_OPTIONS.size - 1)
                val interval = ProxyRotationManager.INTERVAL_OPTIONS[intervalIndex]
                rotationManager.enable(ids, interval)

                val firstConfig = rotationManager.getCurrentConfig()
                val configCount = ids.size

                // Dismiss sheet first, then start VPN on the activity
                sheet.dismiss()
                Snackbar.make(containerView, getString(R.string.rotation_started, configCount), Snackbar.LENGTH_SHORT).show()
                if (firstConfig != null) {
                    hostEditText.setText(firstConfig.proxyAddress)
                    startVpn(firstConfig.proxyAddress)
                }
            } else {
                rotationManager.disable()
                statusText.visibility = View.GONE
                val wasActive = Tun2SocksVpnService.isActive
                sheet.dismiss()
                Snackbar.make(containerView, R.string.rotation_stopped, Snackbar.LENGTH_SHORT).show()
                // Stop the VPN after the sheet is fully dismissed
                if (wasActive) {
                    containerView.postDelayed({
                        stopVpn()
                    }, 300)
                }
                return@setOnCheckedChangeListener
            }
        }

        // Add config button
        btnAdd.setOnClickListener {
            showRotationConfigPicker(view, rotationConfigs) { picked ->
                rotationConfigs.add(picked)
                configAdapter_refresh(configAdapter_ref = recyclerView, configs = rotationConfigs, emptyState = emptyState)
            }
        }

        sheet.setContentView(view)
        sheet.show()
    }

    private fun configAdapter_refresh(configAdapter_ref: RecyclerView, configs: List<ProxyConfig>, emptyState: TextView) {
        (configAdapter_ref.adapter as? RotationConfigAdapter)?.submitList(configs)
        emptyState.visibility = if (configs.isEmpty()) View.VISIBLE else View.GONE
        configAdapter_ref.visibility = if (configs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showRotationConfigPicker(anchorView: View, existing: List<ProxyConfig>, onPick: (ProxyConfig) -> Unit) {
        val allConfigs = configRepository.getAll()
        val existingIds = existing.map { it.id }.toSet()
        val available = allConfigs.filter { it.id !in existingIds }

        if (available.isEmpty()) {
            Snackbar.make(anchorView, R.string.rotation_already_added, Snackbar.LENGTH_SHORT).show()
            return
        }

        val names = available.map { "${it.name} (${it.protocol.uppercase()})" }.toTypedArray()
        MaterialAlertDialogBuilder(this, R.style.Widget_ProxyMe_Dialog_Default)
            .setTitle(R.string.rotation_pick_title)
            .setItems(names) { _, which ->
                onPick(available[which])
            }
            .show()
    }

    // --- Clipboard Detection ---

    private fun checkClipboardForProxy() {
        if (currentVpnState != VpnState.DISCONNECTED) return
        val detected = clipboardDetector.checkClipboard() ?: return
        Snackbar.make(containerView, getString(R.string.clipboard_detected), Snackbar.LENGTH_LONG)
            .setAction(R.string.clipboard_action_use) {
                hostEditText.setText(detected)
                hostLayout.error = null
            }
            .show()
    }

    // --- Import Configs ---

    private fun showImportDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_import, null)
        val importInput = dialogView.findViewById<TextInputEditText>(R.id.import_input)

        MaterialAlertDialogBuilder(this, R.style.Widget_ProxyMe_Dialog_Default)
            .setTitle(R.string.import_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_import) { _, _ ->
                val text = importInput.text?.toString()?.trim() ?: ""
                if (text.isEmpty()) return@setPositiveButton
                val result = ConfigImporter.fromJson(text)
                if (result.configs.isEmpty()) {
                    val msg = if (result.errors.isNotEmpty()) result.errors.first() else getString(R.string.import_empty)
                    Snackbar.make(containerView, getString(R.string.import_failed, msg), Snackbar.LENGTH_LONG).show()
                } else {
                    result.configs.forEach { configRepository.save(it) }
                    if (result.errors.isEmpty()) {
                        Snackbar.make(containerView, getString(R.string.import_success, result.configs.size), Snackbar.LENGTH_LONG).show()
                    } else {
                        Snackbar.make(containerView, getString(R.string.import_partial, result.configs.size, result.configs.size + result.errors.size, result.errors.size), Snackbar.LENGTH_LONG).show()
                    }
                }
                hideKeyboard()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // --- Onboarding ---

    private fun showOnboardingIfFirstRun() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("onboarding_shown", false)) return
        prefs.edit().putBoolean("onboarding_shown", true).apply()

        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_onboarding, null)
        view.findViewById<MaterialButton>(R.id.btn_get_started).setOnClickListener {
            sheet.dismiss()
        }
        sheet.setCancelable(false)
        sheet.setContentView(view)
        sheet.show()
    }

    // --- About ---

    private fun showAbout() {
        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_about, null)

        view.findViewById<TextView>(R.id.about_version).text = "v$versionName"

        view.findViewById<MaterialButton>(R.id.about_github).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_github_url))))
        }

        sheet.setContentView(view)
        sheet.show()
    }

    // --- Lifecycle Callbacks ---

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                startService(intentVPNService)
            } else {
                applyVpnStateToUI(VpnState.DISCONNECTED)
            }
        }
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun loadHostPort() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val userConfig = prefs.getString(PREF_USER_CONFIG, "")
        if (TextUtils.isEmpty(userConfig)) {
            hostEditText.setText(String.format("%s:%s", getString(R.string.ip), getString(R.string.port)))
            return
        }
        hostEditText.setText(userConfig)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(containerView, R.string.notification_permission_granted, Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(containerView, R.string.notification_permission_required, Snackbar.LENGTH_SHORT).show()
                startNotificationSetting()
            }
        }
    }

    private fun startNotificationSetting() {
        try {
            val intent = Intent().apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                putExtra("app_package", applicationInfo.packageName)
                putExtra("android.provider.extra.APP_PACKAGE", applicationInfo.packageName)
                putExtra("app_uid", applicationInfo.uid)
            }
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent().apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                data = Uri.fromParts("package", applicationInfo.packageName, null)
            }
            startActivity(intent)
        }
    }

    private val versionName: String?
        get() = try {
            packageManager?.getPackageInfo(packageName, 0)?.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
}
