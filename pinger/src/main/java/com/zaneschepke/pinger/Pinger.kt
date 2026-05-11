package com.zaneschepke.pinger

import com.zaneschepke.pinger.model.PingConfig
import com.zaneschepke.pinger.model.PingStats

interface Pinger {
    suspend fun ping(config: PingConfig): PingStats
}
