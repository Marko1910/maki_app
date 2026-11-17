package pe.ruma.app.data

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WeekProgress(val days: Int, val goal: Int, val left: Int, val streak: Int)

/** Participation only: distinct Lima dates, Monday through Sunday. Never awards points. */
fun weekProgress(dates: List<Date>, goal: Int, now: Date = Date()): WeekProgress {
    require(goal in 1..7)
    val byWeek = dates.map(::limaDay).groupBy(::weekOf).mapValues { (_, days) -> days.toSet().size }
    val current = weekOf(limaDay(now))
    fun met(week: Long) = (byWeek[week] ?: 0) >= goal
    val days = byWeek[current] ?: 0
    var streak = if (met(current)) 1 else 0
    var week = current - 1
    while (met(week)) { streak++; week-- }
    return WeekProgress(days, goal, (goal - days).coerceAtLeast(0), streak)
}

private fun weekOf(day: Long): Long = Math.floorDiv(day + 3, 7L)

/** Epoch day of the calendar date in Lima, independent of the device's timezone. */
private fun limaDay(at: Date): Long {
    val lima = Calendar.getInstance(TimeZone.getTimeZone("America/Lima"), Locale.US).apply { time = at }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
        clear()
        set(lima.get(Calendar.YEAR), lima.get(Calendar.MONTH), lima.get(Calendar.DAY_OF_MONTH))
    }
    return Math.floorDiv(utc.timeInMillis, 86_400_000L)
}

/** PostgREST timestamps may include microseconds and an explicit timezone offset. */
internal fun parseScanDate(timestamp: String): Date {
    val normalized = timestamp.replace(Regex("\\.(\\d+)(?=Z|[+-]\\d{2}:\\d{2}$)")) {
        "." + it.groupValues[1].take(3).padEnd(3, '0')
    }
    val pattern = if (normalized.contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" else "yyyy-MM-dd'T'HH:mm:ssXXX"
    val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
    val position = ParsePosition(0)
    return parser.parse(normalized, position)?.takeIf { position.index == normalized.length }
        ?: throw IllegalArgumentException("No pudimos leer las fechas de tus registros.")
}

// ---- Tu ruma: the month's pile, one of five 3D renders (res/drawable-nodpi/ruma_1..5) ----
// It grows with constancy (days with a scan), never with volume: counting containers would reward
// producing more waste. docs/retencion-incentivos-research.md, recommendation 4. Mirrors pwa/src/lib/week.ts.

/** Days of the month with a scan needed for each stage; the last one is the strapped bale (about two a week). */
val RUMA_STEPS = listOf(0, 2, 4, 6, 8)

data class RumaLevel(val stage: Int, val next: Int?, val progress: Float)

/** Stage 1..5 for [days] with a scan this month, the days for the next stage, and the fraction of the way there. */
fun rumaLevel(days: Int): RumaLevel {
    val i = RUMA_STEPS.indexOfLast { days >= it }.coerceAtLeast(0)
    val next = RUMA_STEPS.getOrNull(i + 1)
    val progress = if (next == null) 1f else (days - RUMA_STEPS[i]).toFloat() / (next - RUMA_STEPS[i])
    return RumaLevel(i + 1, next, progress)
}

/** Distinct Lima dates in [now]'s Lima month with at least one saved scan. */
fun monthDays(dates: List<Date>, now: Date = Date()): Int {
    fun month(at: Date) = Calendar.getInstance(TimeZone.getTimeZone("America/Lima"), Locale.US)
        .apply { time = at }.let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }
    val current = month(now)
    return dates.filter { month(it) == current }.map(::limaDay).toSet().size
}
