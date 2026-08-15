package com.wafflestudio.team8server.course.sync

import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.syncwithsite.service.ParsedSugangPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class CourseSyncSchedulerTest {
    private val service = mock(CourseSyncService::class.java)
    private val scheduler = CourseSyncScheduler(service)

    @Test
    fun `tick stops while automatic sync is disabled`() {
        `when`(service.isEnabled()).thenReturn(false)

        scheduler.tick()

        verify(service, never()).automaticTarget()
    }

    @Test
    fun `tick skips when the saved schedule cannot provide a target`() {
        `when`(service.isEnabled()).thenReturn(true)
        `when`(service.automaticTarget()).thenReturn(null)

        scheduler.tick()

        verify(service, never()).runOnce(2026, Semester.FALL)
    }

    @Test
    fun `tick syncs and checks snapshot only when automatic conditions pass`() {
        val target = ParsedSugangPeriod(2026, Semester.FALL, null, null, null)
        val activeService =
            mock(CourseSyncService::class.java) { invocation ->
                when (invocation.method.name) {
                    "isEnabled", "shouldRunAutomatically" -> true
                    "automaticTarget" -> target
                    else -> RETURNS_DEFAULTS.answer(invocation)
                }
            }

        CourseSyncScheduler(activeService).tick()

        verify(activeService).runOnce(2026, Semester.FALL)
        assertThat(mockingDetails(activeService).invocations.map { it.method.name })
            .contains("captureCartSnapshotIfDue")
    }

    @Test
    fun `tick skips sync when the target is outside automatic conditions`() {
        val target = ParsedSugangPeriod(2026, Semester.FALL, null, null, null)
        val inactiveService =
            mock(CourseSyncService::class.java) { invocation ->
                when (invocation.method.name) {
                    "isEnabled" -> true
                    "automaticTarget" -> target
                    "shouldRunAutomatically" -> false
                    else -> RETURNS_DEFAULTS.answer(invocation)
                }
            }

        CourseSyncScheduler(inactiveService).tick()

        verify(inactiveService, never()).runOnce(2026, Semester.FALL)
    }
}
