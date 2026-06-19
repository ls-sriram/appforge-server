package com.appforge.server.services.system

import com.appforge.server.config.options.RuntimeOptions
interface SystemServices {
    val runtimeOptions: RuntimeOptions
    val healthUseCases: HealthUseCases
    val systemUseCases: SystemUseCases
}
