package com.appforge.server.middleware

import io.ktor.server.application.Application

/**
 * Test-scope shim for route tests that call configureErrorHandling().
 * Production wiring remains in src/main.
 */
fun Application.configureErrorHandling() = Unit
