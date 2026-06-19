package com.appforge.server.providers.featureflag

import com.appforge.server.config.options.RuntimeOptions

enum class FeatureFlag {
    EARLY_ACCESS_ENABLED,
}

interface FeatureFlagProvider {
    fun isEnabled(flag: FeatureFlag): Boolean
}

class RuntimeFeatureFlagProvider(
    private val runtime: RuntimeOptions,
) : FeatureFlagProvider {
    override fun isEnabled(flag: FeatureFlag): Boolean {
        return when (flag) {
            FeatureFlag.EARLY_ACCESS_ENABLED -> runtime.earlyAccessEnabled
        }
    }
}
