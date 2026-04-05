package tun.proxy.model

data class ConnectionEvent(
    val timestamp: Long,
    val action: String,
    val protocol: String,
    val duration: Long? = null,
    val error: String? = null
)
