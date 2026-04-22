package com.zaneschepke.tunnel

import org.json.JSONObject

object DnsConfigManager {
    private external fun setDNSConfig(configJson: String)

    fun update(protocol: String, upstream: String) {
        val config = JSONObject().apply {
            put("protocol", protocol)
            put("upstream", upstream)
        }
        setDNSConfig(config.toString())
    }
}