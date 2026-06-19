package com.appforge.server.config

import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConfigReaderTest {

    @Test
    fun `reads from config if present`() {
        val config = ConfigFactory.parseMap(mapOf("key" to "value"))
        val reader = ConfigReader(config, emptyMap())
        assertEquals("value", reader.string("key"))
    }

    @Test
    fun `reads from env if config is missing`() {
        val reader = ConfigReader(ConfigFactory.empty(), mapOf("KEY" to "env-value"))
        assertEquals("env-value", reader.string("KEY"))
    }

    @Test
    fun `requiredString throws if missing`() {
        val reader = ConfigReader(ConfigFactory.empty(), emptyMap())
        assertThrows<IllegalStateException> {
            reader.requiredString("MISSING")
        }
    }

    @Test
    fun `reads types correctly`() {
        val config = ConfigFactory.parseMap(mapOf(
            "int" to 123,
            "long" to 123L,
            "bool" to true,
            "list" to listOf("a", "b")
        ))
        val reader = ConfigReader(config, emptyMap())
        
        assertEquals(123, reader.int("int"))
        assertEquals(123L, reader.long("long"))
        assertEquals(true, reader.bool("bool"))
        assertEquals(listOf("a", "b"), reader.stringList("list"))
    }

    @Test
    fun `parses list from env string`() {
        val reader = ConfigReader(ConfigFactory.empty(), mapOf("LIST" to "a,  b, c"))
        assertEquals(listOf("a", "b", "c"), reader.stringList("LIST"))
    }

    @Test
    fun `returns null if missing`() {
        val reader = ConfigReader(ConfigFactory.empty(), emptyMap())
        assertNull(reader.string("missing"))
        assertNull(reader.int("missing"))
        assertNull(reader.bool("missing"))
    }
}
