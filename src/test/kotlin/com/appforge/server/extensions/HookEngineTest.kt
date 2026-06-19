package com.appforge.server.extensions

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class HookEngineTest {

    // ─── Registration ─────────────────────────────────────────────────────

    @Test
    fun `register and fire before hook`() = runBlocking {
        val engine = HookEngine()
        val called = mutableListOf<String>()

        engine.register("test-ext", HookRegistration("before-entity-created") { payload ->
            called.add("handler: ${payload["name"]}")
            HookResponse(allow = true)
        })

        engine.fireBefore("before-entity-created", mapOf("name" to "TestEntity"))
        assertEquals(listOf("handler: TestEntity"), called)
    }

    // ─── Before hooks: blocking, first denial blocks ──────────────────────

    @Test
    fun `before hook can deny operation`() = runBlocking {
        val engine = HookEngine()

        engine.register("ext-a", HookRegistration("before-entity-created") { _ ->
            HookResponse(allow = false, reason = "Not allowed")
        })

        val result = engine.fireBefore("before-entity-created", mapOf("name" to "Test"))
        assertFalse(result.allowed)
        assertEquals("ext-a", result.deniedBy)
        assertEquals("Not allowed", result.reason)
    }

    @Test
    fun `first denial blocks subsequent handlers`() = runBlocking {
        val engine = HookEngine()
        val called = mutableListOf<String>()

        engine.register("ext-a", HookRegistration("before-entity-created") { _ ->
            called.add("ext-a")
            HookResponse(allow = false, reason = "blocked")
        })
        engine.register("ext-b", HookRegistration("before-entity-created") { _ ->
            called.add("ext-b")
            HookResponse(allow = true)
        })

        val result = engine.fireBefore("before-entity-created", emptyMap())
        assertFalse(result.allowed)
        assertEquals(listOf("ext-a"), called) // ext-b should NOT be called
    }

    @Test
    fun `all before hooks must allow for operation to proceed`() = runBlocking {
        val engine = HookEngine()

        engine.register("ext-a", HookRegistration("before-entity-created") { _ ->
            HookResponse(allow = true)
        })
        engine.register("ext-b", HookRegistration("before-entity-created") { _ ->
            HookResponse(allow = true)
        })

        val result = engine.fireBefore("before-entity-created", emptyMap())
        assertTrue(result.allowed)
    }

    @Test
    fun `no registered hooks returns allowed`() = runBlocking {
        val engine = HookEngine()
        val result = engine.fireBefore("unknown-event", emptyMap())
        assertTrue(result.allowed)
    }

    // ─── Before hooks: error handling (fail open) ────────────────────────

    @Test
    fun `handler exception fails open (allows operation)`() = runBlocking {
        val engine = HookEngine()

        engine.register("ext-bad", HookRegistration("before-entity-created") { _ ->
            throw RuntimeException("Handler crashed!")
        })

        val result = engine.fireBefore("before-entity-created", emptyMap())
        assertTrue(result.allowed) // Should fail open, not crash
    }

    // ─── After hooks: fire-and-forget, concurrent ────────────────────────

    @Test
    fun `after hooks fire all handlers concurrently`() = runBlocking {
        val engine = HookEngine()
        val called = CopyOnWriteArrayList<String>()

        engine.register("ext-a", HookRegistration("after-entity-created") { _ ->
            delay(50) // simulate slow handler
            called.add("ext-a")
            HookResponse(allow = true)
        })
        engine.register("ext-b", HookRegistration("after-entity-created") { _ ->
            delay(10)
            called.add("ext-b")
            HookResponse(allow = true)
        })

        engine.fireAfter("after-entity-created", emptyMap())

        // Give async handlers time to complete
        delay(200)
        assertEquals(setOf("ext-a", "ext-b"), called.toSet())
    }

    @Test
    fun `after hook exception does not affect other handlers`() = runBlocking {
        val engine = HookEngine()
        val called = mutableListOf<String>()

        engine.register("ext-bad", HookRegistration("after-entity-created") { _ ->
            throw RuntimeException("Handler crashed!")
        })
        engine.register("ext-good", HookRegistration("after-entity-created") { _ ->
            called.add("ext-good")
            HookResponse(allow = true)
        })

        engine.fireAfter("after-entity-created", emptyMap())
        delay(200)

        assertEquals(listOf("ext-good"), called)
    }

    // ─── Unregister ──────────────────────────────────────────────────────

    @Test
    fun `unregisterExtension removes all hooks for that extension`() = runBlocking {
        val engine = HookEngine()
        val called = mutableListOf<String>()

        engine.register("ext-a", HookRegistration("before-entity-created") { _ ->
            called.add("ext-a-before")
            HookResponse(allow = true)
        })
        engine.register("ext-a", HookRegistration("after-entity-created") { _ ->
            called.add("ext-a-after")
            HookResponse(allow = true)
        })
        engine.register("ext-b", HookRegistration("before-entity-created") { _ ->
            called.add("ext-b-before")
            HookResponse(allow = true)
        })

        engine.unregisterExtension("ext-a")

        engine.fireBefore("before-entity-created", emptyMap())
        assertEquals(listOf("ext-b-before"), called)
    }

    // ─── Multiple event types ────────────────────────────────────────────

    @Test
    fun `handlers only fire for their registered event type`() = runBlocking {
        val engine = HookEngine()
        val called = mutableListOf<String>()

        engine.register("ext-a", HookRegistration("before-entity-created") { _ ->
            called.add("created")
            HookResponse(allow = true)
        })
        engine.register("ext-a", HookRegistration("before-review-submitted") { _ ->
            called.add("reviewed")
            HookResponse(allow = true)
        })

        engine.fireBefore("before-entity-created", emptyMap())
        assertEquals(listOf("created"), called)
    }

    // ─── Hook events constants exist ─────────────────────────────────────

    @Test
    fun `hook event constants are defined`() {
        assertNotNull(HookEvents.BEFORE_ENTITY_CREATED)
        assertNotNull(HookEvents.AFTER_ENTITY_CREATED)
        assertNotNull(HookEvents.BEFORE_ENTITY_UPDATED)
        assertNotNull(HookEvents.AFTER_ENTITY_UPDATED)
        assertNotNull(HookEvents.BEFORE_REVIEW_SUBMITTED)
        assertNotNull(HookEvents.AFTER_REVIEW_SUBMITTED)
        assertNotNull(HookEvents.BEFORE_SHARE_CREATED)
        assertNotNull(HookEvents.AFTER_SHARE_CREATED)
        assertNotNull(HookEvents.ON_ENTITLEMENT_CHANGED)
        assertNotNull(HookEvents.ON_USER_LOGIN)
    }
}
