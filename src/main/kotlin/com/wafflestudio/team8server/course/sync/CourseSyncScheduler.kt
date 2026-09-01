package com.wafflestudio.team8server.course.sync

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CourseSyncScheduler(
    private val service: CourseSyncService,
) {
    private val log = LoggerFactory.getLogger(CourseSyncScheduler::class.java)

    @Scheduled(fixedDelayString = "\${course-sync.auto.fixedDelayMillis:7200000}")
    fun tick() {
        if (!service.isEnabled()) return

        val target = service.automaticTarget()
        if (target == null) {
            log.warn("Auto course sync is enabled but no parsable saved sugang period exists. Skipping.")
            return
        }

        if (service.shouldRunAutomatically(target)) {
            try {
                service.runOnce(target.year, target.semester)
            } catch (e: Exception) {
                log.error("Automatic course sync failed", e)
            }
        }
        try {
            service.captureCartSnapshotIfDue(target)?.let { captured ->
                log.info("Automatic course cart snapshot success (rows={})", captured)
            }
        } catch (e: Exception) {
            log.error("Automatic course cart snapshot failed", e)
        }
    }
}
