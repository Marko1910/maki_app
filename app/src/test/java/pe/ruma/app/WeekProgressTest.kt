package pe.ruma.app

import pe.ruma.app.data.WeekProgress
import pe.ruma.app.data.parseScanDate
import pe.ruma.app.data.RumaLevel
import pe.ruma.app.data.monthDays
import pe.ruma.app.data.rumaLevel
import pe.ruma.app.data.weekProgress
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/** All seven assertions from pwa/scripts/check-week.ts, plus timestamp/device edge cases. */
class WeekProgressTest {
    private val now = parseScanDate("2026-11-04T17:00:00Z")
    private fun dates(vararg days: String) = days.map { parseScanDate("${it}T15:00:00Z") }
    private val lastTwoWeeks = dates("2026-10-19", "2026-10-20", "2026-10-21", "2026-10-26", "2026-10-27", "2026-10-28")

    @Test fun rumaStagesByDays() {
        assertEquals(RumaLevel(1, 2, 0f), rumaLevel(0))
        assertEquals(RumaLevel(2, 4, 0.5f), rumaLevel(3))
        assertEquals(4, rumaLevel(7).stage)
        assertEquals(RumaLevel(5, null, 1f), rumaLevel(8))
        assertEquals(RumaLevel(5, null, 1f), rumaLevel(31))
    }
    @Test fun rumaCountsLimaDaysOfTheMonth() {
        val nov = parseScanDate("2026-11-20T17:00:00Z")
        // Two scans on 3 Nov count once; 1 Nov 03:00 UTC is still 31 Oct in Lima.
        val dates = listOf("2026-11-03T15:00:00Z", "2026-11-03T22:00:00Z", "2026-11-10T15:00:00Z", "2026-11-01T03:00:00Z").map(::parseScanDate)
        assertEquals(2, monthDays(dates, nov))
    }
    @Test fun emptyHistory() {
        assertEquals(WeekProgress(0, 3, 3, 0), weekProgress(emptyList(), 3, now))
    }
    @Test fun sameLimaDayCountsOnce() {
        assertEquals(1, weekProgress(listOf(parseScanDate("2026-11-02T15:00:00Z"), parseScanDate("2026-11-02T22:00:00Z")), 3, now).days)
    }
    @Test fun nextUtcDateCanStillBeSameLimaDay() {
        assertEquals(1, weekProgress(listOf(parseScanDate("2026-11-02T15:00:00Z"), parseScanDate("2026-11-03T03:00:00Z")), 3, now).days)
    }
    @Test fun sundayNightBelongsToPreviousWeek() {
        assertEquals(0, weekProgress(listOf(parseScanDate("2026-11-02T04:30:00Z")), 1, now).days)
    }
    @Test fun unfinishedCurrentWeekKeepsPastStreak() {
        assertEquals(2, weekProgress(lastTwoWeeks + dates("2026-11-02"), 3, now).streak)
    }
    @Test fun completedCurrentWeekJoinsStreak() {
        assertEquals(WeekProgress(3, 3, 0, 3), weekProgress(lastTwoWeeks + dates("2026-11-02", "2026-11-03", "2026-11-04"), 3, now))
    }
    @Test fun missedWeekEndsStreak() {
        assertEquals(0, weekProgress(dates("2026-10-19", "2026-10-20", "2026-10-21"), 3, now).streak)
    }
    @Test fun exceedsGoalWithoutNegativeRemainingDays() {
        assertEquals(WeekProgress(3, 1, 0, 1), weekProgress(dates("2026-11-02", "2026-11-03", "2026-11-04"), 1, now))
    }
    @Test fun explicitOffsetAndMicroseconds() {
        assertEquals(parseScanDate("2026-11-03T03:00:00.123Z"), parseScanDate("2026-11-02T22:00:00.123456-05:00"))
    }
    @Test fun deviceTimezoneDoesNotChangeMondayBoundary() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(WeekProgress(1, 1, 0, 2), weekProgress(listOf(
                parseScanDate("2026-11-02T04:59:59Z"), parseScanDate("2026-11-02T05:00:00Z"),
            ), 1, now))
        } finally { TimeZone.setDefault(original) }
    }
}
