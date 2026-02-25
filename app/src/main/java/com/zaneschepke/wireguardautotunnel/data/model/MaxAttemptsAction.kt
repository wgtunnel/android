package com.zaneschepke.wireguardautotunnel.data.model

enum class MaxAttemptsAction(val value: Int) {
    DO_NOTHING(0),
    STOP_TUNNEL(1);

    companion object {
        fun fromValue(value: Int): MaxAttemptsAction = entries.find { it.value == value } ?: DO_NOTHING
    }
}
