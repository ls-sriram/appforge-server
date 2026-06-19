package com.appforge.server.services.sharing.models

enum class ShareAccessMode(val wire: String) {
    PUBLIC_LINK("public_link");

    companion object {
        fun fromWire(value: String): ShareAccessMode {
            return entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("Unsupported share access mode: $value")
        }
    }
}

