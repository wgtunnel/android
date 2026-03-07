package com.zaneschepke.wireguardautotunnel.di

// Dispatchers
enum class Dispatcher {
    MAIN,
    IO,
    DEFAULT,
    MAIN_IMMEDIATE,
}

// Scopes
enum class Scope {
    APPLICATION
}

enum class Shell {
    APP,
    TUNNEL,
}

enum class Core {
    KERNEL,
    PROXY_USERSPACE,
    USERSPACE,
}
