package com.wafflestudio.team8server.syncwithsite.repository

import com.wafflestudio.team8server.course.model.Semester
import com.wafflestudio.team8server.syncwithsite.model.SyncWithSiteRun
import com.wafflestudio.team8server.syncwithsite.model.SyncWithSiteRunStatus
import com.wafflestudio.team8server.syncwithsite.model.SyncWithSiteSetting
import com.wafflestudio.team8server.syncwithsite.model.SugangPeriodSnapshot
import org.springframework.data.jpa.repository.JpaRepository

interface SyncWithSiteSettingRepository : JpaRepository<SyncWithSiteSetting, Long>

interface SyncWithSiteRunRepository : JpaRepository<SyncWithSiteRun, Long> {
    fun findTopByOrderByStartedAtDesc(): SyncWithSiteRun?

    fun findFirstByStatusOrderByStartedAtDesc(status: SyncWithSiteRunStatus): SyncWithSiteRun?
}

interface SugangPeriodSnapshotRepository : JpaRepository<SugangPeriodSnapshot, Long> {
    fun findByYearAndSemester(
        year: Int,
        semester: Semester,
    ): SugangPeriodSnapshot?
}
