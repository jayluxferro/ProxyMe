package tun.proxy.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import tun.proxy.model.ProxyConfig
import java.util.UUID

data class ImportResult(
    val configs: List<ProxyConfig>,
    val errors: List<String>
)

object ConfigImporter {

    fun fromJson(json: String): ImportResult {
        val configs = mutableListOf<ProxyConfig>()
        val errors = mutableListOf<String>()

        try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                // JSON array of configs
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val list: List<Map<String, Any>> = Gson().fromJson(trimmed, type)
                for ((i, item) in list.withIndex()) {
                    try {
                        configs.add(mapToConfig(item))
                    } catch (e: Exception) {
                        errors.add("Item ${i + 1}: ${e.message}")
                    }
                }
            } else if (trimmed.startsWith("{")) {
                // Single config object
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val item: Map<String, Any> = Gson().fromJson(trimmed, type)
                configs.add(mapToConfig(item))
            } else {
                // Try line-by-line proxy URLs
                trimmed.lines().filter { it.isNotBlank() }.forEachIndexed { i, line ->
                    try {
                        configs.add(proxyUrlToConfig(line.trim(), "Imported #${i + 1}"))
                    } catch (e: Exception) {
                        errors.add("Line ${i + 1}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Parse error: ${e.message}")
        }

        return ImportResult(configs, errors)
    }

    private fun mapToConfig(map: Map<String, Any>): ProxyConfig {
        val host = (map["host"] ?: map["server"] ?: map["addr"])?.toString()
            ?: throw IllegalArgumentException("Missing host/server field")
        val port = (map["port"])?.toString()?.toDoubleOrNull()?.toInt()
            ?: throw IllegalArgumentException("Missing or invalid port")
        val protocol = (map["protocol"] ?: map["type"] ?: "http").toString().lowercase()
        val name = (map["name"] ?: map["remarks"] ?: "$host:$port").toString()
        val username = map["username"]?.toString() ?: map["user"]?.toString()
        val password = map["password"]?.toString() ?: map["pass"]?.toString()
        val authEnabled = !username.isNullOrEmpty() && !password.isNullOrEmpty()

        return ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = if (protocol.contains("socks")) "socks5" else "http",
            host = host,
            port = port,
            authEnabled = authEnabled,
            username = if (authEnabled) username else null,
            password = if (authEnabled) password else null,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun proxyUrlToConfig(url: String, defaultName: String): ProxyConfig {
        val regex = """(?:(socks5|http)://)?(?:(\w+):(\w+)@)?([\w.\-]+):(\d+)""".toRegex()
        val match = regex.find(url) ?: throw IllegalArgumentException("Invalid proxy format")
        val (type, user, pass, host, port) = match.destructured
        val authEnabled = user.isNotEmpty() && pass.isNotEmpty()

        return ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = defaultName,
            protocol = if (type == "socks5") "socks5" else "http",
            host = host,
            port = port.toInt(),
            authEnabled = authEnabled,
            username = if (authEnabled) user else null,
            password = if (authEnabled) pass else null,
            createdAt = System.currentTimeMillis()
        )
    }
}
