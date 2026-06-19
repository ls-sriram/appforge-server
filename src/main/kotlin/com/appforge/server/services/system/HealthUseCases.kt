package com.appforge.server.services.system

interface HealthUseCases {
    fun status(): String
}

class HealthUseCasesImpl : HealthUseCases {
    override fun status(): String = "ok"
}
