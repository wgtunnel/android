package com.zaneschepke.tunnel.model

import java.net.InetAddress

data class DnsConfig(val dnsServers: List<InetAddress>, val searchDomains: List<String>)
