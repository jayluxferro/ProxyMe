package tun.proxy.model

data class ProxyData(
    val proxyType: String = "http",
    val proxyUser: String? = null,
    val proxyPass: String? = null,
    val proxyHost: String,
    val proxyPort: Int
)
