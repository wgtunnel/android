package com.zaneschepke.wireguardautotunnel.core.service

import com.zaneschepke.wireguardautotunnel.util.Constants

class TunnelForegroundService(override val fgsType: Int = Constants.SPECIAL_USE_SERVICE_TYPE_ID) :
    BaseTunnelForegroundService()
