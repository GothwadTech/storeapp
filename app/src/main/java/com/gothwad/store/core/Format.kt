package com.gothwad.store.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor

/** Formatting shared by every screen so numbers never look inconsistent. */
object Format {

    fun bytes(value: Long): String {
        if (value < 0) return "—"
        val kb = 1024.0
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= kb && unit < units.lastIndex) {
            size /= kb
            unit++
        }
        val decimals = if (size >= 100 || unit == 0) 0 else 1
        return String.format(Locale.US, "%.${decimals}f %s", size, units[unit])
    }

    /** Play-style install counts: 999 · 4.2K · 1.2M · 3B (truncated, not rounded). */
    fun installs(value: Long): String = when {
        value <= 0 -> "0"
        value < 1_000 -> value.toString()
        value < 1_000_000 -> magnitude(value / 1_000.0, "K")
        value < 1_000_000_000 -> magnitude(value / 1_000_000.0, "M")
        else -> magnitude(value / 1_000_000_000.0, "B")
    }

    /** `1.2K` under ten, `42K` above it — never rounds up past the real count. */
    private fun magnitude(value: Double, suffix: String): String =
        if (value < 10) {
            String.format(Locale.US, "%.1f%s", floor(value * 10) / 10, suffix)
        } else {
            "${value.toLong()}$suffix"
        }

    fun reviews(value: Int): String = when {
        value <= 0 -> "No ratings"
        value == 1 -> "1 rating"
        value < 10_000 -> String.format(Locale.US, "%,d ratings", value)
        else -> "${installs(value.toLong())} ratings"
    }

    fun rating(value: Double): String =
        if (value <= 0) "New" else String.format(Locale.US, "%.1f", value)

    fun stars(value: Double): String {
        val full = value.toInt().coerceIn(0, 5)
        return "★".repeat(full) + "☆".repeat(5 - full)
    }

    fun isoDate(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val cleaned = raw.substringBefore('.').substringBefore('+').replace('T', ' ')
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val date = parser.parse(cleaned) ?: return raw.take(10)
            SimpleDateFormat("d MMM yyyy", Locale.US).format(date)
        } catch (_: Exception) {
            raw.take(10)
        }
    }

    fun relative(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val millis = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .parse(raw.substringBefore('.').substringBefore('+'))?.time
        }.getOrNull() ?: return isoDate(raw)
        return relative(millis)
    }

    fun relative(millis: Long): String {
        val delta = System.currentTimeMillis() - millis
        if (abs(delta) < TimeUnit.MINUTES.toMillis(1)) return "just now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        val hours = TimeUnit.MILLISECONDS.toHours(delta)
        val days = TimeUnit.MILLISECONDS.toDays(delta)
        return when {
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 30 -> "${days}d ago"
            else -> SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(millis))
        }
    }

    fun speed(bytesPerSecond: Long): String = "${bytes(bytesPerSecond)}/s"

    fun eta(remainingBytes: Long, bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0 || remainingBytes <= 0) return "—"
        val seconds = remainingBytes / bytesPerSecond
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            else -> "${seconds / 3600}h"
        }
    }

    /** "android.permission.CAMERA" -> "Camera" */
    fun permission(raw: String): String {
        val short = raw.substringAfterLast('.').replace('_', ' ').trim()
        if (short.isEmpty()) return "Unknown permission"
        return short.lowercase().replaceFirstChar { it.uppercase() }
    }

    fun packageLabel(pkg: String): String = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    fun initials(name: String): String {
        val parts = name.trim().split(' ', '.', '_', '-').filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "G"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }
}
