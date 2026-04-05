package tun.proxy.service

enum class VpnState {
    CONNECTING, CONNECTED, FAILED, DISCONNECTED
}

const val ACTION_VPN_STATE_CHANGED = "tun.proxy.VPN_STATE_CHANGED"
const val EXTRA_VPN_STATE = "vpn_state"
const val EXTRA_ERROR_MESSAGE = "error_message"
