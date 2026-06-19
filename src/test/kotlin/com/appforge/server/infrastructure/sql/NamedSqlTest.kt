package com.appforge.server.infrastructure.sql

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NamedSqlTest {
    @Test
    fun `loads named queries from auth sql resource`() {
        val namedSql = NamedSql.fromResource(
            resourcePath = "com/appforge/server/services/auth/auth.sql",
            classLoader = NamedSqlTest::class.java.classLoader,
        )

        val query = namedSql.query("user.upsert_app_user")
        assertTrue(query.contains("INSERT INTO app_users"))
    }

    @Test
    fun `throws on missing query key`() {
        val namedSql = NamedSql.fromResource(
            resourcePath = "com/appforge/server/services/auth/auth.sql",
            classLoader = NamedSqlTest::class.java.classLoader,
        )

        assertFailsWith<IllegalStateException> {
            namedSql.query("user.does_not_exist")
        }
    }

    @Test
    fun `throws on missing sql resource`() {
        assertFailsWith<IllegalStateException> {
            NamedSql.fromResource(
                resourcePath = "com/appforge/server/services/auth/repository/missing.sql",
                classLoader = NamedSqlTest::class.java.classLoader,
            )
        }
    }
}
