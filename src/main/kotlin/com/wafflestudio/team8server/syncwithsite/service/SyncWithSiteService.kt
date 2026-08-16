package com.wafflestudio.team8server.syncwithsite.service
import com.wafflestudio.team8server.common.exception.ResourceNotFoundException
import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodDto
import com.wafflestudio.team8server.syncwithsite.dto.SugangPeriodResponse
import com.wafflestudio.team8server.syncwithsite.model.SugangPeriodSnapshot
import com.wafflestudio.team8server.syncwithsite.model.SyncWithSiteRun
import com.wafflestudio.team8server.syncwithsite.model.SyncWithSiteRunStatus
import com.wafflestudio.team8server.syncwithsite.model.SyncWithSiteSetting
import com.wafflestudio.team8server.syncwithsite.repository.SugangPeriodSnapshotRepository
import com.wafflestudio.team8server.syncwithsite.repository.SyncWithSiteRunRepository
import com.wafflestudio.team8server.syncwithsite.repository.SyncWithSiteSettingRepository
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

@Service
class SyncWithSiteService(
    private val settingRepository: SyncWithSiteSettingRepository,
    private val runRepository: SyncWithSiteRunRepository,
    private val snapshotRepository: SugangPeriodSnapshotRepository,
    private val objectMapper: ObjectMapper,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(SyncWithSiteService::class.java)
    private val running = AtomicBoolean(false)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    companion object {
        private const val SUGANG_URL = "https://sugang.snu.ac.kr/sugang/co/co010.action"
        private const val TIMEOUT_MS = 15_000
    }

    private fun textWithBr(elements: Elements): String {
        if (elements.isEmpty()) return ""
        val raw =
            elements.joinToString("\n") { elem ->
                val clone = elem.clone()
                clone.select("br").forEach { it.replaceWith(TextNode("\n")) }
                clone.wholeText()
            }
        return raw
            .replace("\r\n", "\n")
            .replace(Regex("\\n[ \\t]+"), " ")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()
    }

    fun crawlSugangPeriod(): SugangPeriodResponse {
        // Jsoup HTTP 요청으로 크롤링
        val document =
            Jsoup
                .connect(SUGANG_URL)
                .cookie("enter", "Y")
                .header("Referer", "https://sugang.snu.ac.kr/")
                .userAgent("Mozilla/5.0 (compatible; SnuclearBot/1.0)")
                .timeout(TIMEOUT_MS)
                .get()

        // Select header and table body both (container)
        val container =
            document.select("div.mg-item.mg-period-guide .con-box").firstOrNull()
                ?: throw ResourceNotFoundException("Cannot found SugangPeriod container")

        // Extract h2 header
        val headerElements = container.select("h2")
        if (headerElements.isEmpty()) {
            throw ResourceNotFoundException("Cannot found SugangPeriod h2 header")
        }

        // "{abcd}학년도 {n}학기 수강신청 기간안내 ※ 장바구니는 선착순이 아닙니다."
        val headerText = textWithBr(headerElements)

        // Extract table
        val tableElement =
            container.select("div.table-con table").firstOrNull()
                ?: throw IllegalStateException("Cannot found SugangPeriod table")

        // Parse table elements
        val body = mutableListOf<SugangPeriodDto>()
        val rows = tableElement.select("tbody tr")
        for (row in rows) {
            val category = textWithBr(row.select("th[data-th=구분]"))
            val date = textWithBr(row.select("td[data-th=일자]"))
            val time = textWithBr(row.select("td[data-th=시간]"))
            val remark = textWithBr(row.select("td[data-th=대상]"))

            if (category.isNotBlank() || date.isNotBlank()) {
                body.add(
                    SugangPeriodDto(
                        category = category,
                        date = date,
                        time = time,
                        remark = remark,
                    ),
                )
            }
        }

        return SugangPeriodResponse(
            header = headerText,
            body = body,
        )
    }

    @Transactional
    fun enableAuto(): SyncWithSiteSetting {
        val cur = settingRepository.findById(1L).orElse(SyncWithSiteSetting(id = 1L))
        val updated = SyncWithSiteSetting(id = 1L, enabled = true, updatedAt = LocalDateTime.now())
        return settingRepository.save(updated.copyFrom(cur))
    }

    @Transactional
    fun disableAuto(): SyncWithSiteSetting {
        val cur = settingRepository.findById(1L).orElse(SyncWithSiteSetting(id = 1L))
        val updated = SyncWithSiteSetting(id = 1L, enabled = false, updatedAt = LocalDateTime.now())
        return settingRepository.save(updated.copyFrom(cur))
    }

    fun getSetting(): SyncWithSiteSetting =
        settingRepository.findById(1L).orElse(SyncWithSiteSetting(id = 1L, enabled = false, updatedAt = LocalDateTime.now()))

    fun getLastRun(): SyncWithSiteRun? = runRepository.findTopByOrderByStartedAtDesc()

    fun getLastSugangPeriod(): SugangPeriodResponse? =
        runRepository
            .findFirstByStatusOrderByStartedAtDesc(SyncWithSiteRunStatus.SUCCESS)
            ?.dumpedData
            ?.let { objectMapper.readValue(it, SugangPeriodResponse::class.java) }

    fun getSavedSugangPeriod(): SugangPeriodResponse? {
        val latest = getLastSugangPeriod() ?: return null
        val target = SugangPeriodParser.parse(latest) ?: return latest
        return snapshotRepository
            .findByYearAndSemester(target.year, target.semester)
            ?.let { objectMapper.readValue(it.dumpedData, SugangPeriodResponse::class.java) }
            ?: latest
    }

    fun isEnabled(): Boolean = getSetting().enabled

    fun runOnce() {
        if (!running.compareAndSet(false, true)) {
            throw IllegalStateException("SyncWithSite is already running")
        }

        val startedAt = LocalDateTime.now()
        try {
            log.info("SyncWithSite sync started")

            // Crawl information from sugang sites
            val result = crawlSugangPeriod()

            // Serialize the values
            val dumpedJson = objectMapper.writeValueAsString(result)

            transactionTemplate.executeWithoutResult {
                runRepository.save(
                    SyncWithSiteRun(
                        status = SyncWithSiteRunStatus.SUCCESS,
                        startedAt = startedAt,
                        finishedAt = LocalDateTime.now(),
                        dumpedData = dumpedJson,
                        message = null,
                    ),
                )
                saveSugangPeriodSnapshot(result)
            }
            log.info("SyncWithSite sync success")
        } catch (e: Exception) {
            // Save Error logs to the DB
            runRepository.save(
                SyncWithSiteRun(
                    status = SyncWithSiteRunStatus.FAILED,
                    startedAt = startedAt,
                    finishedAt = LocalDateTime.now(),
                    dumpedData = null,
                    message = (e.message ?: e.javaClass.simpleName).take(500),
                ),
            )
            throw e
        } finally {
            running.set(false)
        }
    }

    private fun SyncWithSiteSetting.copyFrom(prev: SyncWithSiteSetting): SyncWithSiteSetting =
        SyncWithSiteSetting(id = prev.id, enabled = this.enabled, updatedAt = this.updatedAt)

    fun getSugangPeriod(): SugangPeriodResponse {
        val lastSuccessRun = runRepository.findFirstByStatusOrderByStartedAtDesc(SyncWithSiteRunStatus.SUCCESS)
        val period = getSavedSugangPeriod()
        if (period != null) {
            log.info("Returning sugang period from DB dump (runId: {})", lastSuccessRun?.id)
            return period
        }

        // Fallback logic
        log.info("No dumped data found in DB. Falling back to real-time crawling.")
        return crawlSugangPeriod()
    }

    private fun saveSugangPeriodSnapshot(crawled: SugangPeriodResponse) {
        val target = SugangPeriodParser.parse(crawled) ?: return
        val snapshot = snapshotRepository.findByYearAndSemester(target.year, target.semester)
        if (snapshot == null) {
            val saved =
                runRepository
                    .findAllByStatusOrderByStartedAtAsc(SyncWithSiteRunStatus.SUCCESS)
                    .mapNotNull { it.dumpedData }
                    .map { objectMapper.readValue(it, SugangPeriodResponse::class.java) }
                    .filter {
                        SugangPeriodParser.parse(it)?.let { parsed ->
                            parsed.year == target.year && parsed.semester == target.semester
                        } == true
                    }.reduce(SugangPeriodSnapshotMerger::merge)
            snapshotRepository.save(
                SugangPeriodSnapshot(
                    year = target.year,
                    semester = target.semester,
                    dumpedData = objectMapper.writeValueAsString(saved),
                    updatedAt = LocalDateTime.now(),
                ),
            )
            return
        }

        val saved = objectMapper.readValue(snapshot.dumpedData, SugangPeriodResponse::class.java)
        val merged = SugangPeriodSnapshotMerger.merge(saved, crawled)
        if (merged == saved) return

        snapshot.dumpedData = objectMapper.writeValueAsString(merged)
        snapshot.updatedAt = LocalDateTime.now()
        snapshotRepository.save(snapshot)
    }
}
