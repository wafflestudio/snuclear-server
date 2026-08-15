package com.wafflestudio.team8server.syncwithsite.service

import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodResponse

object SugangPeriodSnapshotMerger {
    fun merge(
        saved: SugangPeriodResponse,
        crawled: SugangPeriodResponse,
    ): SugangPeriodResponse {
        if (saved.body.takeLast(crawled.body.size) == crawled.body) {
            return if (saved.header == crawled.header) saved else saved.copy(header = crawled.header)
        }

        val preserved = saved.body.take((saved.body.size - crawled.body.size).coerceAtLeast(0))
        return crawled.copy(body = preserved + crawled.body)
    }
}
