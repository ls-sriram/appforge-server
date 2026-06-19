package com.appforge.server.providers.config

import com.appforge.server.config.AppEnv

interface ConfigProvider {
    val env: AppEnv
}

class AppConfigProvider(
    override val env: AppEnv,
) : ConfigProvider
