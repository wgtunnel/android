package com.zaneschepke.wireguardautotunnel.util.extensions

import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.parser.Config
import com.zaneschepke.wireguardautotunnel.parser.ConfigParseException
import com.zaneschepke.wireguardautotunnel.util.NumberUtils
import com.zaneschepke.wireguardautotunnel.util.StringValue

fun ConfigParseException.asStringValue(): StringValue {
    return StringValue.StringResource(
        R.string.config_error_template,
        this.errorType.name,
        this.field,
    )
}

fun Config.defaultName(): String {
    return this.peers[0].host ?: NumberUtils.generateRandomTunnelName()
}
