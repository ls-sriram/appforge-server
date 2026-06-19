package com.appforge.server.services.tasks

import com.appforge.server.api.TaskCreateRequest
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.tasks.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskServiceTest {
    @Test
    fun `create rejects blank title`() = runBlocking {
        val repo = mockk<TaskRepository>(relaxed = true)
        val service = TaskServiceImpl(repo)

        val result = service.create(
            userId = "u1",
            request = TaskCreateRequest(type = "follow_up", title = "   "),
        )

        assertTrue(result is AuthResponse.BadRequest)
    }

    @Test
    fun `create trims title and tag`() = runBlocking {
        val repo = mockk<TaskRepository>()
        val now = Instant.parse("2026-05-25T00:00:00Z")
        coEvery { repo.create(any()) } answers {
            firstArg<TaskModel>().copy(createdAt = now, updatedAt = now)
        }

        val service = TaskServiceImpl(repo)
        val result = service.create(
            userId = "u1",
            request = TaskCreateRequest(type = "follow_up", title = "  Email school  ", tag = "  app  "),
        )

        assertTrue(result is AuthResponse.Ok)
        assertEquals("Email school", result.data.title)
        assertEquals("app", result.data.tag)
    }
}
