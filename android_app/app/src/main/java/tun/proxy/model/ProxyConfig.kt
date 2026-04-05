package tun.proxy.model

data class ProxyConfig(
    val id: String,
    val name: String,
    val protocol: String = "http",
    val host: String,
    val port: Int,
    val authEnabled: Boolean = false,
    val username: String? = null,
    val password: String? = null,
    val createdAt: Long
) {
    val proxyAddress: String
        get() = buildString {
            append("$protocol://")
            if (authEnabled && !username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                append("$username:$password@")
            }
            append("$host:$port")
        }

    val displayAddress: String
        get() = buildString {
            append("$protocol://")
            if (authEnabled && !username.isNullOrEmpty()) {
                append("***@")
            }
            append("$host:$port")
        }
}
