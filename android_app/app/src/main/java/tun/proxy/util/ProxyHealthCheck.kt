package tun.proxy.util

import java.net.InetSocketAddress
import java.net.Socket

data class HealthResult(
    val reachable: Boolean,
    val latencyMs: Long = -1,
    val error: String? = null
)

object ProxyHealthCheck {

    fun test(host: String, port: Int, timeoutMs: Int = 5000): HealthResult {
        val socket = Socket()
        return try {
            val start = System.currentTimeMillis()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            val latency = System.currentTimeMillis() - start
            HealthResult(reachable = true, latencyMs = latency)
        } catch (e: Exception) {
            HealthResult(reachable = false, error = e.message ?: "Connection failed")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
