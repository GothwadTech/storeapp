package com.gothwad.store.data.remote

import com.gothwad.store.data.model.CatalogResponse
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreRelease
import com.squareup.moshi.JsonReader

/**
 * The small subset of F-Droid's v1 index that Kiwi Store retains.
 *
 * `apps` in index-v1.json is an array, while `packages` is an object keyed by
 * package name. The parser below streams both sections and keeps only the
 * newest APK compatible with this device, rather than building a second huge
 * copy of the several-megabyte index in memory.
 */
data class FdroidIndex(
    val apps: List<FdroidApp>,
    val packages: Map<String, FdroidApk>,
)

data class FdroidApp(
    val packageName: String,
    val name: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val webSite: String? = null,
    val authorName: String? = null,
    val authorEmail: String? = null,
    val categories: List<String> = emptyList(),
    /** English localization, or the best available fallback localization. */
    val localized: FdroidLocalized? = null,
)

data class FdroidLocalized(
    val name: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val icon: String? = null,
)

data class FdroidApk(
    val apkName: String? = null,
    val versionCode: Long? = null,
    val versionName: String? = null,
    val size: Long? = null,
    val hash: String? = null,
    val hashType: String? = null,
    val minSdkVersion: Int? = null,
    val maxSdkVersion: Int? = null,
    val targetSdkVersion: Int? = null,
)

/** Streaming parser for the official F-Droid index-v1 wire format. */
object FdroidIndexParser {

    /** Reads the large index without materialising its full JSON tree. */
    fun parse(reader: JsonReader, sdkInt: Int): FdroidIndex {
        val apps = LinkedHashMap<String, FdroidApp>()
        val compatibleApks = LinkedHashMap<String, FdroidApk>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "apps" -> readApps(reader, apps)
                "packages" -> readPackages(reader, sdkInt, compatibleApks)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        check(apps.isNotEmpty()) {
            "F-Droid index did not contain any app metadata (expected an 'apps' array)."
        }
        return FdroidIndex(apps.values.toList(), compatibleApks)
    }

    private fun readApps(reader: JsonReader, apps: MutableMap<String, FdroidApp>) {
        check(reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
            "F-Droid index 'apps' field must be a JSON array."
        }
        reader.beginArray()
        while (reader.hasNext()) {
            readApp(reader)?.let { app -> apps[app.packageName] = app }
        }
        reader.endArray()
    }

    private fun readApp(reader: JsonReader): FdroidApp? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }

        var packageName: String? = null
        var name: String? = null
        var summary: String? = null
        var description: String? = null
        var icon: String? = null
        var website: String? = null
        var authorName: String? = null
        var authorEmail: String? = null
        var categories: List<String> = emptyList()
        var localized: FdroidLocalized? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "packageName" -> packageName = reader.readStringValue()
                "name" -> name = reader.readStringValue()
                "summary" -> summary = reader.readStringValue()
                "description" -> description = reader.readStringValue()
                "icon" -> icon = reader.readStringValue()
                "webSite" -> website = reader.readStringValue()
                "authorName" -> authorName = reader.readStringValue()
                "authorEmail" -> authorEmail = reader.readStringValue()
                "categories" -> categories = readStringArray(reader)
                "localized" -> localized = readLocalized(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val pkg = packageName?.trim().orEmpty()
        if (pkg.isEmpty()) return null
        return FdroidApp(
            packageName = pkg,
            name = name,
            summary = summary,
            description = description,
            icon = icon,
            webSite = website,
            authorName = authorName,
            authorEmail = authorEmail,
            categories = categories,
            localized = localized,
        )
    }

    private fun readLocalized(reader: JsonReader): FdroidLocalized? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }

        var english: FdroidLocalized? = null
        var fallback: FdroidLocalized? = null
        reader.beginObject()
        while (reader.hasNext()) {
            val locale = reader.nextName()
            val value = readLocalizedEntry(reader) ?: continue
            if (locale.equals("en-US", ignoreCase = true)) {
                english = value
            } else if (fallback == null && value.hasMetadata()) {
                fallback = value
            }
        }
        reader.endObject()

        // Prefer each English field, falling back to another translation when
        // the English localization is incomplete.
        return mergeLocalized(english, fallback)
    }

    private fun readLocalizedEntry(reader: JsonReader): FdroidLocalized? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var name: String? = null
        var summary: String? = null
        var description: String? = null
        var icon: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = reader.readStringValue()
                "summary" -> summary = reader.readStringValue()
                "description" -> description = reader.readStringValue()
                "icon" -> icon = reader.readStringValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return FdroidLocalized(name, summary, description, icon)
    }

    private fun FdroidLocalized.hasMetadata(): Boolean =
        !name.isNullOrBlank() || !summary.isNullOrBlank() ||
            !description.isNullOrBlank() || !icon.isNullOrBlank()

    private fun mergeLocalized(
        preferred: FdroidLocalized?,
        fallback: FdroidLocalized?,
    ): FdroidLocalized? {
        if (preferred == null) return fallback
        if (fallback == null) return preferred
        fun choose(first: String?, second: String?): String? = first?.takeIf { it.isNotBlank() } ?: second
        return FdroidLocalized(
            name = choose(preferred.name, fallback.name),
            summary = choose(preferred.summary, fallback.summary),
            description = choose(preferred.description, fallback.description),
            icon = choose(preferred.icon, fallback.icon),
        )
    }

    private fun readStringArray(reader: JsonReader): List<String> {
        if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) {
            reader.skipValue()
            return emptyList()
        }
        val values = ArrayList<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            reader.readStringValue()?.takeIf { it.isNotBlank() }?.let { values.add(it) }
        }
        reader.endArray()
        return values
    }

    private fun readPackages(
        reader: JsonReader,
        sdkInt: Int,
        compatibleApks: MutableMap<String, FdroidApk>,
    ) {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val packageName = reader.nextName()
            readPackageVersions(reader, sdkInt)?.let { compatibleApks[packageName] = it }
        }
        reader.endObject()
    }

    private fun readPackageVersions(reader: JsonReader, sdkInt: Int): FdroidApk? {
        if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) {
            reader.skipValue()
            return null
        }
        var best: FdroidApk? = null
        reader.beginArray()
        while (reader.hasNext()) {
            val apk = readApk(reader) ?: continue
            val minSdk = apk.minSdkVersion ?: 1
            val maxSdk = apk.maxSdkVersion
            if (minSdk > sdkInt || (maxSdk != null && maxSdk < sdkInt) || apk.apkName.isNullOrBlank()) continue
            if (best == null || (apk.versionCode ?: 0L) > (best.versionCode ?: 0L)) best = apk
        }
        reader.endArray()
        return best
    }

    private fun readApk(reader: JsonReader): FdroidApk? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var apkName: String? = null
        var versionCode: Long? = null
        var versionName: String? = null
        var size: Long? = null
        var hash: String? = null
        var hashType: String? = null
        var minSdkVersion: Int? = null
        var maxSdkVersion: Int? = null
        var targetSdkVersion: Int? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "apkName" -> apkName = reader.readStringValue()
                "versionCode" -> versionCode = reader.readLongValue()
                "versionName" -> versionName = reader.readStringValue()
                "size" -> size = reader.readLongValue()
                "hash" -> hash = reader.readStringValue()
                "hashType" -> hashType = reader.readStringValue()
                "minSdkVersion" -> minSdkVersion = reader.readLongValue()?.toIntOrNull()
                "maxSdkVersion" -> maxSdkVersion = reader.readLongValue()?.toIntOrNull()
                "targetSdkVersion" -> targetSdkVersion = reader.readLongValue()?.toIntOrNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return FdroidApk(
            apkName = apkName,
            versionCode = versionCode,
            versionName = versionName,
            size = size,
            hash = hash,
            hashType = hashType,
            minSdkVersion = minSdkVersion,
            maxSdkVersion = maxSdkVersion,
            targetSdkVersion = targetSdkVersion,
        )
    }

    private fun JsonReader.readStringValue(): String? = when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any>()
            null
        }
        JsonReader.Token.STRING, JsonReader.Token.NUMBER -> nextString()
        else -> {
            skipValue()
            null
        }
    }

    private fun JsonReader.readLongValue(): Long? = when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any>()
            null
        }
        JsonReader.Token.NUMBER -> nextLong()
        JsonReader.Token.STRING -> nextString().toLongOrNull()
        else -> {
            skipValue()
            null
        }
    }

    private fun Long.toIntOrNull(): Int? =
        takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
}

/**
 * F-Droid category names mapped onto Kiwi Store category slugs. The upstream
 * and first-party catalogues use different vocabularies, so category filters
 * must be normalized before apps can show up on category screens.
 */
object FdroidCategories {

    private val slugs = mapOf(
        "internet" to "browser",
        "web" to "browser",
        "connectivity" to "tools",
        "phone & sms" to "communication",
        "contacts" to "communication",
        "instant messaging" to "communication",
        "messaging" to "communication",
        "email" to "communication",
        "system" to "tools",
        "development" to "tools",
        "programming & development" to "tools",
        "file managers" to "tools",
        "files" to "tools",
        "theming" to "tools",
        "privacy" to "tools",
        "security" to "tools",
        "navigation & maps" to "tools",
        "weather" to "tools",
        "remote" to "tools",
        "tethering" to "tools",
        "status bar" to "tools",
        "quick settings" to "tools",
        "screen lock" to "tools",
        "medical" to "tools",
        "writing & notes" to "productivity",
        "text & notes" to "productivity",
        "productivity" to "productivity",
        "time & calendar" to "productivity",
        "calendar & agenda" to "productivity",
        "note" to "productivity",
        "task" to "productivity",
        "science & education" to "education",
        "education" to "education",
        "book" to "education",
        "reference" to "education",
        "kids" to "education",
        "multimedia" to "music",
        "music & audio" to "music",
        "audio" to "music",
        "video" to "entertainment",
        "photography" to "photo",
        "images" to "photo",
        "wallpapers" to "photo",
        "games" to "entertainment",
        "comics" to "entertainment",
        "manga" to "entertainment",
        "anime" to "entertainment",
        "news & magazines" to "entertainment",
        "readers & news" to "entertainment",
        "sports & health" to "entertainment",
        "money" to "finance",
        "finance" to "finance",
    )

    /** Used when F-Droid lists a category Kiwi Store does not know. */
    const val DEFAULT_SLUG = "tools"

    /** The Kiwi Store slug for one F-Droid category, or null when unknown. */
    fun slug(category: String?): String? {
        val key = category?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return null
        slugs[key]?.let { return it }
        // F-Droid splits games into many categories ("Party Game", "Arcade
        // Game", …), while Kiwi Store keeps them under one slug.
        if (key.contains("game")) return "entertainment"
        return null
    }

    fun slugFor(categories: List<String>): String =
        categories.firstNotNullOfOrNull { slug(it) } ?: DEFAULT_SLUG

    /** F-Droid keeps games in a category, not in a kind. */
    fun isGame(categories: List<String>): Boolean =
        categories.any { it.trim().contains("game", ignoreCase = true) }
}

/** Converts the streamed index into the store's models. */
fun FdroidIndex.toCatalog(repoUrl: String): CatalogResponse {
    val releasesByPackage = packages
    val listings = ArrayList<StoreApp>(apps.size)
    val releases = ArrayList<StoreRelease>(apps.size)

    for (meta in apps) {
        val packageName = meta.packageName
        val apk = releasesByPackage[packageName] ?: continue
        val apkName = apk.apkName?.trim().orEmpty()
        if (apkName.isBlank()) continue

        val versionCode = apk.versionCode ?: 0L
        val localized = meta.localized
        val name = localized?.name?.trim().orEmpty()
            .ifBlank { meta.name?.trim().orEmpty() }
            .ifBlank { packageName.substringAfterLast('.') }
        val summary = localized?.summary?.trim().orEmpty()
            .ifBlank { meta.summary?.trim().orEmpty() }
            .ifBlank { localized?.description?.trim().orEmpty() }
            .ifBlank { meta.description?.trim().orEmpty() }
        val description = localized?.description?.trim().orEmpty()
            .ifBlank { meta.description?.trim().orEmpty() }
            .ifBlank { summary }

        val categories = meta.categories.mapNotNull { category ->
            category.trim().takeIf { it.isNotEmpty() }
        }
        val id = "fdroid:$packageName"

        listings += StoreApp(
            id = id,
            packageName = packageName,
            name = name,
            developerName = meta.authorName?.trim().orEmpty()
                .ifBlank { meta.authorEmail?.trim().orEmpty() }
                .ifBlank { "Open-source developer" },
            category = FdroidCategories.slugFor(categories),
            kind = if (FdroidCategories.isGame(categories)) "game" else "app",
            status = "published",
            shortDesc = summary,
            fullDesc = description,
            iconUrl = fdroidIconUrl(meta, packageName, repoUrl),
            website = meta.webSite?.trim()?.takeIf { it.isNotBlank() },
            source = "F-Droid",
        )

        releases += StoreRelease(
            id = id,
            appId = id,
            versionCode = versionCode,
            versionName = apk.versionName?.trim().orEmpty().ifBlank { versionCode.toString() },
            apkProvider = "fdroid",
            apkKey = apkName,
            apkUrl = "$repoUrl/$apkName",
            apkSizeBytes = apk.size ?: 0L,
            apkSha256 = apk.hash?.trim().orEmpty().takeIf {
                it.isNotBlank() && apk.hashType.orEmpty().ifBlank { "sha256" }.equals("sha256", ignoreCase = true)
            },
            minSdk = apk.minSdkVersion ?: 1,
            targetSdk = apk.targetSdkVersion?.takeIf { it > 0 },
            reviewStatus = "approved",
            isLive = true,
        )
    }

    return CatalogResponse(apps = listings, releases = releases, source = "F-Droid")
}

/**
 * The index's app icon is the versioned image in icons-640/. The localized
 * icon is a fallback hosted under the package's locale directory.
 */
private fun fdroidIconUrl(meta: FdroidApp, packageName: String, repoUrl: String): String {
    meta.icon?.trim()?.takeIf { it.isNotBlank() }?.let { return "$repoUrl/icons-640/$it" }
    meta.localized?.icon?.trim()?.takeIf { it.isNotBlank() }?.let { return "$repoUrl/$packageName/en-US/$it" }
    return ""
}
