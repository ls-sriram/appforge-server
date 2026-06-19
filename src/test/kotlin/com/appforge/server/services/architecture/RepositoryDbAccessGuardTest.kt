package com.appforge.server.services.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.fail

class RepositoryDbAccessGuardTest {
    @Test
    fun `repositories must not call withConnection directly`() {
        val root = Path.of("src/main/kotlin/com.appforge/server/services")
        val offenders = mutableListOf<String>()

        Files.walk(root).use { paths ->
            paths.filter { path ->
                path.isRegularFile() &&
                    path.extension == "kt" &&
                    path.toString().contains("/repository/") &&
                    path.name.endsWith("Repository.kt")
            }.forEach { file ->
                val content = Files.readString(file)
                if (content.contains("withConnection(")) {
                    offenders += file.toString()
                }
            }
        }

        if (offenders.isNotEmpty()) {
            fail("Repository DB access guard failed. Use TransactionProvider instead of withConnection. Offenders: ${offenders.joinToString()}")
        }
    }
}
