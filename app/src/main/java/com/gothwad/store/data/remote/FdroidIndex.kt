package com.gothwad.store.data.remote

import com.gothwad.store.data.model.CatalogResponse
import com.gothwad.store.data.model.StoreApp
import com.gothwad.store.data.model.StoreRelease
import com.squareup.moshi.JsonReader
import okio.BufferedSource
import okio.GzipSource
import okio.buffer
import okio.source
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * The subset of F-Droid's v1 (`index-v1.jar` / `index-v1.json`) and v2
 * (`index-v2.json`) repository indexes that Kiwi Store retains.
 *
 * Modeled after Droid-ify (`IndexV1Parser.kt`, `IndexConverter.kt`) and the
 * official F-Droid client (`IndexV1Updater.kt`, `AppV1.kt`):
 * - `apps` in `index-v1.json` is an array while `packages` is an object keyed
 *   by package name.
 * - Supports streaming directly from `index-v1.jar` (ZIP/JAR archive containing
 *   `index-v1.json`), which is how both Droid-ify and official F-Droid fetch
 *   the index (~8.8 MB vs ~40+ MB uncompressed).
 * - Retains only the best compatible APK per package (checking `minSdkVersion`,
 *   `maxSdkVersion`, and device `nativecode` ABIs).
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
    val sourceCode: String? = null,
    val license: String? = null,
    val authorName: String? = null,
    val authorEmail: String? = null,
    val categories: List<String> = emptyList(),
    /** English localization, or the best available fallback localization. */
    val localized: FdroidLocalized? = null,
    val localizedLocale: String = "en-US",
    val suggestedVersionCode: Long? = null,
    val added: Long? = null,
    val lastUpdated: Long? = null,
)

data class FdroidLocalized(
    val name: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val featureGraphic: String? = null,
    val phoneScreenshots: List<String> = emptyList(),
    val whatsNew: String? = null,
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
    val permissions: List<String> = emptyList(),
    val nativecode: List<String> = emptyList(),
    val added: Long? = null,
    val whatsNew: String? = null,
)

/**
 * Streaming parser for the official F-Droid `index-v1.jar` / `index-v1.json`
 * and `index-v2.json` wire formats.
 */
object FdroidIndexParser {

    /**
     * Parses a `.jar` / `.zip` archive (extracting `index-v1.json` or
     * `index-v2.json` via [ZipFile]) or a plain `.json` file from disk.
     */
    fun parseFile(
        file: File,
        sdkInt: Int,
        supportedAbis: Set<String> = emptySet(),
    ): FdroidIndex {
        if (isZipFile(file)) {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("index-v1.json")
                    ?: zip.getEntry("index-v2.json")
                    ?: zip.entries().asSequence().firstOrNull {
                        !it.isDirectory && it.name.endsWith(".json", ignoreCase = true) &&
                            !it.name.endsWith("entry.json", ignoreCase = true)
                    }
                    ?: error("F-Droid archive ${file.name} does not contain index-v1.json or index-v2.json.")
                zip.getInputStream(entry).source().buffer().use { buffered ->
                    return parse(JsonReader.of(buffered), sdkInt, supportedAbis)
                }
            }
        }
        file.source().buffer().use { buffered ->
            return parseSource(buffered, sdkInt, supportedAbis)
        }
    }

    /**
     * Automatically detects whether [source] is a `.jar` / `.zip` stream
     * (`PK\x03\x04`), a `.gz` stream (`\x1f\x8b`), or raw JSON, and streams
     * the inner JSON index through [parse].
     */
    fun parseSource(
        source: BufferedSource,
        sdkInt: Int,
        supportedAbis: Set<String> = emptySet(),
    ): FdroidIndex {
        val peek = source.peek()
        if (peek.request(4)) {
            val b0 = peek.readByte().toInt() and 0xFF
            val b1 = peek.readByte().toInt() and 0xFF
            val b2 = peek.readByte().toInt() and 0xFF
            val b3 = peek.readByte().toInt() and 0xFF
            // ZIP / JAR magic header: PK\x03\x04
            if (b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04) {
                val zis = ZipInputStream(source.inputStream())
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (!entry.isDirectory &&
                        (entry.name == "index-v1.json" ||
                            entry.name == "index-v2.json" ||
                            (entry.name.endsWith(".json", ignoreCase = true) &&
                                !entry.name.endsWith("entry.json", ignoreCase = true)))
                    ) {
                        return parse(JsonReader.of(zis.source().buffer()), sdkInt, supportedAbis)
                    }
                }
                error("F-Droid JAR stream did not contain index-v1.json or index-v2.json.")
            }
            // GZIP magic header: 0x1F 0x8B
            if (b0 == 0x1F && b1 == 0x8B) {
                GzipSource(source).buffer().use { unzipped ->
                    return parseSource(unzipped, sdkInt, supportedAbis)
                }
            }
        }
        return parse(JsonReader.of(source), sdkInt, supportedAbis)
    }

    /** Reads the large index without materialising its full JSON tree. */
    fun parse(
        reader: JsonReader,
        sdkInt: Int,
        supportedAbis: Set<String> = emptySet(),
    ): FdroidIndex {
        val apps = LinkedHashMap<String, FdroidApp>()
        val rawPackageCandidates = LinkedHashMap<String, List<FdroidApk>>()
        val compatibleApks = LinkedHashMap<String, FdroidApk>()

        check(reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
            "Expected JSON object at root of F-Droid index, found ${reader.peek()}"
        }
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "apps" -> readApps(reader, apps)
                "packages" -> readPackages(reader, apps, rawPackageCandidates)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        for ((packageName, candidates) in rawPackageCandidates) {
            val suggestedCode = apps[packageName]?.suggestedVersionCode
            selectBestApk(candidates, sdkInt, supportedAbis, suggestedCode)?.let { best ->
                compatibleApks[packageName] = best
            }
        }

        check(apps.isNotEmpty()) {
            "F-Droid index did not contain any app metadata (expected an 'apps' array)."
        }
        return FdroidIndex(apps.values.toList(), compatibleApks)
    }

    private fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val b0 = raf.read()
                val b1 = raf.read()
                val b2 = raf.read()
                val b3 = raf.read()
                b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04
            }
        }.getOrDefault(false)
    }

    internal fun selectBestApk(
        candidates: List<FdroidApk>,
        sdkInt: Int,
        supportedAbis: Set<String>,
        suggestedVersionCode: Long?,
    ): FdroidApk? {
        val sdkCompatible = candidates.filter { apk ->
            val minSdk = apk.minSdkVersion ?: 1
            val maxSdk = apk.maxSdkVersion
            !apk.apkName.isNullOrBlank() &&
                minSdk <= sdkInt &&
                (maxSdk == null || maxSdk <= 0 || maxSdk >= sdkInt)
        }
        if (sdkCompatible.isEmpty()) return null

        val abiOrder = supportedAbis.toList()
        val abiFiltered = if (abiOrder.isEmpty()) {
            sdkCompatible
        } else {
            sdkCompatible.filter { apk ->
                apk.nativecode.isEmpty() || apk.nativecode.any { it in supportedAbis }
            }.ifEmpty { sdkCompatible }
        }

        fun abiScore(apk: FdroidApk): Int {
            if (abiOrder.isEmpty() || apk.nativecode.isEmpty()) return 1
            var best = 0
            for (abi in apk.nativecode) {
                val idx = abiOrder.indexOf(abi)
                if (idx >= 0) {
                    val score = (abiOrder.size - idx) + 1
                    if (score > best) best = score
                }
            }
            return best
        }

        return abiFiltered.maxWithOrNull(
            compareBy<FdroidApk> { it.versionCode ?: 0L }
                .thenBy { apk ->
                    if (suggestedVersionCode != null && apk.versionCode == suggestedVersionCode) 1 else 0
                }
                .thenBy { apk -> abiScore(apk) }
                .thenBy { apk -> if (apk.nativecode.isEmpty()) 1 else 0 },
        )
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
        var sourceCode: String? = null
        var license: String? = null
        var authorName: String? = null
        var authorEmail: String? = null
        var categories: List<String> = emptyList()
        var localized: FdroidLocalized? = null
        var localizedLocale = "en-US"
        var suggestedVersionCode: Long? = null
        var added: Long? = null
        var lastUpdated: Long? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "packageName" -> packageName = reader.readStringValue()
                "name" -> name = reader.readStringValue()
                "summary" -> summary = reader.readStringValue()
                "description" -> description = reader.readStringValue()
                "icon" -> icon = reader.readStringValue()
                "webSite" -> website = reader.readStringValue()
                "sourceCode" -> sourceCode = reader.readStringValue()
                "license" -> license = reader.readStringValue()
                "authorName" -> authorName = reader.readStringValue()
                "authorEmail" -> authorEmail = reader.readStringValue()
                "categories" -> categories = readStringArray(reader)
                "suggestedVersionCode" -> suggestedVersionCode = reader.readLongValue()
                "added" -> added = reader.readLongValue()
                "lastUpdated" -> lastUpdated = reader.readLongValue()
                "localized" -> {
                    val pair = readLocalized(reader)
                    if (pair != null) {
                        localizedLocale = pair.first
                        localized = pair.second
                    }
                }
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
            sourceCode = sourceCode,
            license = license,
            authorName = authorName,
            authorEmail = authorEmail,
            categories = categories,
            localized = localized,
            localizedLocale = localizedLocale,
            suggestedVersionCode = suggestedVersionCode,
            added = added,
            lastUpdated = lastUpdated,
        )
    }

    private fun readLocalized(reader: JsonReader): Pair<String, FdroidLocalized>? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }

        val byLocale = LinkedHashMap<String, FdroidLocalized>()
        reader.beginObject()
        while (reader.hasNext()) {
            val locale = reader.nextName()
            val value = readLocalizedEntry(reader) ?: continue
            if (value.hasMetadata()) {
                byLocale[locale] = value
            }
        }
        reader.endObject()

        if (byLocale.isEmpty()) return null

        val preferredLocales = listOf("en-US", "en", "en-GB", "en_US", "en-AU", "en-CA")
        val primaryLocale = byLocale.keys.firstOrNull { key ->
            preferredLocales.any { it.equals(key, ignoreCase = true) }
        } ?: byLocale.keys.first()

        val ordered = buildList {
            for (pref in preferredLocales) {
                byLocale.entries.firstOrNull { it.key.equals(pref, ignoreCase = true) }?.let {
                    if (it.toPair() !in this) add(it.toPair())
                }
            }
            for ((loc, entry) in byLocale) {
                val p = loc to entry
                if (p !in this) add(p)
            }
        }

        fun pickString(selector: (FdroidLocalized) -> String?): Pair<String, String>? {
            for ((loc, entry) in ordered) {
                val v = selector(entry)?.takeIf { it.isNotBlank() }
                if (v != null) return loc to v
            }
            return null
        }

        val namePair = pickString { it.name }
        val summaryPair = pickString { it.summary }
        val descPair = pickString { it.description }
        val whatsNewPair = pickString { it.whatsNew }
        val iconPair = pickString { it.icon }
        val fgPair = pickString { it.featureGraphic }
        val shotsPair = ordered.firstNotNullOfOrNull { (loc, entry) ->
            entry.phoneScreenshots.takeIf { it.isNotEmpty() }?.let { loc to it }
        }

        val resolvedIcon = iconPair?.let { (loc, iconName) ->
            if (loc.equals(primaryLocale, ignoreCase = true) || iconName.contains("/") || iconName.startsWith("/")) {
                iconName
            } else {
                "$loc/$iconName"
            }
        }
        val resolvedFg = fgPair?.let { (loc, fgName) ->
            if (loc.equals(primaryLocale, ignoreCase = true) || fgName.contains("/") || fgName.startsWith("/")) {
                fgName
            } else {
                "$loc/$fgName"
            }
        }
        val resolvedShots = shotsPair?.let { (loc, shots) ->
            if (loc.equals(primaryLocale, ignoreCase = true)) {
                shots
            } else {
                shots.map { s ->
                    if (s.contains("/") || s.startsWith("/")) s else "$loc/phoneScreenshots/$s"
                }
            }
        }.orEmpty()

        return primaryLocale to FdroidLocalized(
            name = namePair?.second,
            summary = summaryPair?.second,
            description = descPair?.second,
            icon = resolvedIcon,
            featureGraphic = resolvedFg,
            phoneScreenshots = resolvedShots,
            whatsNew = whatsNewPair?.second,
        )
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
        var featureGraphic: String? = null
        val phoneScreenshots = ArrayList<String>()
        val tabletScreenshots = ArrayList<String>()
        var whatsNew: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = reader.readStringValue()
                "summary" -> summary = reader.readStringValue()
                "description" -> description = reader.readStringValue()
                "icon" -> icon = reader.readStringValue()
                "featureGraphic" -> featureGraphic = reader.readStringValue()
                "phoneScreenshots" -> phoneScreenshots.addAll(readStringArray(reader))
                "sevenInchScreenshots" -> {
                    readStringArray(reader).forEach { s ->
                        tabletScreenshots += if (s.contains("/")) s else "sevenInchScreenshots/$s"
                    }
                }
                "tenInchScreenshots" -> {
                    readStringArray(reader).forEach { s ->
                        tabletScreenshots += if (s.contains("/")) s else "tenInchScreenshots/$s"
                    }
                }
                "whatsNew" -> whatsNew = reader.readStringValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return FdroidLocalized(
            name = name,
            summary = summary,
            description = description,
            icon = icon,
            featureGraphic = featureGraphic,
            phoneScreenshots = phoneScreenshots.ifEmpty { tabletScreenshots },
            whatsNew = whatsNew,
        )
    }

    private fun FdroidLocalized.hasMetadata(): Boolean =
        !name.isNullOrBlank() || !summary.isNullOrBlank() ||
            !description.isNullOrBlank() || !icon.isNullOrBlank() ||
            !featureGraphic.isNullOrBlank() || phoneScreenshots.isNotEmpty() ||
            !whatsNew.isNullOrBlank()

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
        apps: MutableMap<String, FdroidApp>,
        rawPackages: MutableMap<String, List<FdroidApk>>,
    ) {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val packageName = reader.nextName()
            when (reader.peek()) {
                JsonReader.Token.BEGIN_ARRAY -> {
                    val versions = readPackageVersionsV1(reader)
                    if (versions.isNotEmpty()) {
                        rawPackages[packageName] = versions
                    }
                }
                JsonReader.Token.BEGIN_OBJECT -> {
                    readPackageEntryV2(reader, packageName, apps, rawPackages)
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readPackageVersionsV1(reader: JsonReader): List<FdroidApk> {
        val list = ArrayList<FdroidApk>(4)
        reader.beginArray()
        while (reader.hasNext()) {
            val apk = readApkV1(reader) ?: continue
            if (!apk.apkName.isNullOrBlank()) {
                list += apk
            }
        }
        reader.endArray()
        return list
    }

    private fun readApkV1(reader: JsonReader): FdroidApk? {
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
        var added: Long? = null
        val permissions = LinkedHashSet<String>()
        val nativecode = ArrayList<String>(4)
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
                "added" -> added = reader.readLongValue()
                "nativecode" -> nativecode.addAll(readStringArray(reader))
                "uses-permission", "uses-permission-sdk-23" -> readPermissionsV1(reader, permissions)
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
            permissions = permissions.toList(),
            nativecode = nativecode,
            added = added,
        )
    }

    private fun readPermissionsV1(reader: JsonReader, into: MutableSet<String>) {
        if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) {
            reader.skipValue()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonReader.Token.BEGIN_ARRAY -> {
                    reader.beginArray()
                    val perm = if (reader.hasNext()) reader.readStringValue() else null
                    while (reader.hasNext()) reader.skipValue()
                    reader.endArray()
                    if (!perm.isNullOrBlank()) into += perm
                }
                JsonReader.Token.STRING -> {
                    val perm = reader.readStringValue()
                    if (!perm.isNullOrBlank()) into += perm
                }
                else -> reader.skipValue()
            }
        }
        reader.endArray()
    }

    /**
     * Parses a package entry from F-Droid `index-v2.json` (`packages[pkg] = { metadata, versions }`).
     */
    private fun readPackageEntryV2(
        reader: JsonReader,
        packageName: String,
        apps: MutableMap<String, FdroidApp>,
        rawPackages: MutableMap<String, List<FdroidApk>>,
    ) {
        reader.beginObject()
        var parsedMeta: FdroidApp? = null
        val versions = ArrayList<FdroidApk>(4)
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "metadata" -> parsedMeta = readMetadataV2(reader, packageName)
                "versions" -> readVersionsMapV2(reader, versions)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (parsedMeta != null) {
            val latestWhatsNew = versions.maxByOrNull { it.versionCode ?: 0L }?.whatsNew
            val updatedMeta = if (!latestWhatsNew.isNullOrBlank() && parsedMeta.localized?.whatsNew.isNullOrBlank()) {
                val baseLoc = parsedMeta.localized ?: FdroidLocalized()
                parsedMeta.copy(localized = baseLoc.copy(whatsNew = latestWhatsNew))
            } else {
                parsedMeta
            }
            apps[packageName] = updatedMeta
        }
        if (versions.isNotEmpty()) {
            rawPackages[packageName] = versions
        }
    }

    private fun readMetadataV2(reader: JsonReader, packageName: String): FdroidApp? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var name: String? = null
        var summary: String? = null
        var description: String? = null
        var icon: String? = null
        var featureGraphic: String? = null
        var authorName: String? = null
        var authorEmail: String? = null
        var webSite: String? = null
        var sourceCode: String? = null
        var license: String? = null
        var categories: List<String> = emptyList()
        var added: Long? = null
        var lastUpdated: Long? = null
        val screenshots = ArrayList<String>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = reader.readLocalizedStringV2()
                "summary" -> summary = reader.readLocalizedStringV2()
                "description" -> description = reader.readLocalizedStringV2()
                "icon" -> icon = readLocalizedFileV2(reader)
                "featureGraphic" -> featureGraphic = readLocalizedFileV2(reader)
                "authorName" -> authorName = reader.readStringValue()
                "authorEmail" -> authorEmail = reader.readStringValue()
                "webSite" -> webSite = reader.readStringValue()
                "sourceCode" -> sourceCode = reader.readStringValue()
                "license" -> license = reader.readStringValue()
                "categories" -> categories = readStringArray(reader)
                "added" -> added = reader.readLongValue()
                "lastUpdated" -> lastUpdated = reader.readLongValue()
                "screenshots" -> readScreenshotsV2(reader, screenshots)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return FdroidApp(
            packageName = packageName,
            name = name,
            summary = summary,
            description = description,
            icon = icon,
            webSite = webSite,
            sourceCode = sourceCode,
            license = license,
            authorName = authorName,
            authorEmail = authorEmail,
            categories = categories,
            localized = FdroidLocalized(
                name = name,
                summary = summary,
                description = description,
                icon = icon,
                featureGraphic = featureGraphic,
                phoneScreenshots = screenshots,
            ),
            added = added,
            lastUpdated = lastUpdated,
        )
    }

    private fun readLocalizedFileV2(reader: JsonReader): String? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        val byLocale = LinkedHashMap<String, String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val locale = reader.nextName()
            val filePath = readFileObjectPathV2(reader)
            if (!filePath.isNullOrBlank()) {
                byLocale[locale] = filePath
            }
        }
        reader.endObject()
        val preferred = listOf("en-US", "en", "en-GB", "en_US")
        return preferred.firstNotNullOfOrNull { byLocale[it] } ?: byLocale.values.firstOrNull()
    }

    private fun readFileObjectPathV2(reader: JsonReader): String? {
        if (reader.peek() == JsonReader.Token.STRING) return reader.readStringValue()
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var name: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = reader.readStringValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return name
    }

    private fun readScreenshotsV2(reader: JsonReader, into: MutableList<String>) {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }
        val phoneList = ArrayList<String>()
        val otherList = ArrayList<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val deviceType = reader.nextName()
            val target = if (deviceType == "phone") phoneList else otherList
            if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                val byLocale = LinkedHashMap<String, List<String>>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val locale = reader.nextName()
                    if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                        val files = ArrayList<String>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            readFileObjectPathV2(reader)?.let(files::add)
                        }
                        reader.endArray()
                        if (files.isNotEmpty()) byLocale[locale] = files
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
                val preferred = listOf("en-US", "en", "en-GB", "en_US")
                val chosen = preferred.firstNotNullOfOrNull { byLocale[it] } ?: byLocale.values.firstOrNull()
                if (chosen != null) target.addAll(chosen)
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        into.addAll(phoneList.ifEmpty { otherList })
    }

    private fun readVersionsMapV2(reader: JsonReader, into: MutableList<FdroidApk>) {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }
        reader.beginObject()
        while (reader.hasNext()) {
            reader.nextName()
            readApkV2(reader)?.let(into::add)
        }
        reader.endObject()
    }

    private fun readApkV2(reader: JsonReader): FdroidApk? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var added: Long? = null
        var apkName: String? = null
        var sha256: String? = null
        var size: Long? = null
        var versionName: String? = null
        var versionCode: Long? = null
        var minSdk: Int? = null
        var targetSdk: Int? = null
        var maxSdk: Int? = null
        var whatsNew: String? = null
        val permissions = LinkedHashSet<String>()
        val nativecode = ArrayList<String>(4)

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "added" -> added = reader.readLongValue()
                "whatsNew" -> whatsNew = reader.readLocalizedStringV2()
                "file" -> {
                    if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> apkName = reader.readStringValue()?.removePrefix("/")
                                "sha256" -> sha256 = reader.readStringValue()
                                "size" -> size = reader.readLongValue()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else {
                        reader.skipValue()
                    }
                }
                "manifest" -> {
                    if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "versionName" -> versionName = reader.readStringValue()
                                "versionCode" -> versionCode = reader.readLongValue()
                                "maxSdkVersion" -> maxSdk = reader.readLongValue()?.toIntOrNull()
                                "nativecode" -> nativecode.addAll(readStringArray(reader))
                                "usesSdk" -> {
                                    if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "minSdkVersion" -> minSdk = reader.readLongValue()?.toIntOrNull()
                                                "targetSdkVersion" -> targetSdk = reader.readLongValue()?.toIntOrNull()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                    } else {
                                        reader.skipValue()
                                    }
                                }
                                "usesPermission", "usesPermissionSdk23" -> readPermissionsV2(reader, permissions)
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (apkName.isNullOrBlank() || versionCode == null) return null
        return FdroidApk(
            apkName = apkName,
            versionCode = versionCode,
            versionName = versionName,
            size = size,
            hash = sha256,
            hashType = "sha256",
            minSdkVersion = minSdk,
            maxSdkVersion = maxSdk,
            targetSdkVersion = targetSdk,
            permissions = permissions.toList(),
            nativecode = nativecode,
            added = added,
            whatsNew = whatsNew,
        )
    }

    private fun readPermissionsV2(reader: JsonReader, into: MutableSet<String>) {
        if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) {
            reader.skipValue()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                var perm: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> perm = reader.readStringValue()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (!perm.isNullOrBlank()) into += perm
            } else {
                reader.skipValue()
            }
        }
        reader.endArray()
    }

    private fun JsonReader.readLocalizedStringV2(): String? = when (peek()) {
        JsonReader.Token.STRING, JsonReader.Token.NUMBER -> nextString()
        JsonReader.Token.BEGIN_OBJECT -> {
            val byLocale = LinkedHashMap<String, String>()
            beginObject()
            while (hasNext()) {
                val loc = nextName()
                val v = readStringValue()
                if (!v.isNullOrBlank()) byLocale[loc] = v
            }
            endObject()
            val preferred = listOf("en-US", "en", "en-GB", "en_US")
            preferred.firstNotNullOfOrNull { byLocale[it] } ?: byLocale.values.firstOrNull()
        }
        else -> {
            skipValue()
            null
        }
    }

    private fun JsonReader.readStringValue(): String? = when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any>()
            null
        }
        JsonReader.Token.STRING, JsonReader.Token.NUMBER -> nextString()
        JsonReader.Token.BOOLEAN -> nextBoolean().toString()
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
        JsonReader.Token.NUMBER -> runCatching { nextLong() }.getOrElse {
            runCatching { nextDouble().toLong() }.getOrNull()
        }
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
        "navigation" to "tools",
        "navigation & maps" to "tools",
        "automation" to "tools",
        "xposed" to "tools",
        "weather" to "tools",
        "remote" to "tools",
        "tethering" to "tools",
        "status bar" to "tools",
        "quick settings" to "tools",
        "screen lock" to "tools",
        "medical" to "tools",
        "writing" to "productivity",
        "reading" to "productivity",
        "office" to "productivity",
        "time" to "productivity",
        "writing & notes" to "productivity",
        "text & notes" to "productivity",
        "productivity" to "productivity",
        "time & calendar" to "productivity",
        "calendar & agenda" to "productivity",
        "note" to "productivity",
        "task" to "productivity",
        "science & education" to "education",
        "education" to "education",
        "religion" to "education",
        "book" to "education",
        "reference" to "education",
        "kids" to "education",
        "multimedia" to "music",
        "music & audio" to "music",
        "audio" to "music",
        "video" to "entertainment",
        "graphics" to "photo",
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
        "food" to "entertainment",
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

    /**
     * Collects all normalized category slugs and aliases for [categories] so
     * that every category tile on Home and Categories (e.g. `games`,
     * `photography`, `books`, `health`, `video`, `news`, `social`, `maps`,
     * `food`, `communication`, `tools`) matches the relevant F-Droid apps.
     */
    fun tagsFor(categories: List<String>): List<String> {
        if (categories.isEmpty()) return listOf(DEFAULT_SLUG)
        val out = LinkedHashSet<String>()
        for (raw in categories) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            out += trimmed
            val lower = trimmed.lowercase()
            slug(trimmed)?.let { out += it }
            when (lower) {
                "games" -> out.addAll(listOf("games", "game", "entertainment"))
                "internet" -> out.addAll(listOf("browser", "communication", "social", "news", "internet"))
                "connectivity" -> out.addAll(listOf("tools", "communication", "connectivity"))
                "messaging", "phone & sms" -> out.addAll(listOf("communication", "social", "messaging"))
                "multimedia" -> out.addAll(listOf("music", "video", "entertainment", "photography", "multimedia"))
                "graphics", "photography" -> out.addAll(listOf("photo", "photography", "graphics"))
                "reading", "writing" -> out.addAll(listOf("productivity", "books", "news", "education", lower))
                "office", "time" -> out.addAll(listOf("productivity", "business", "tools", lower))
                "science & education", "religion" -> out.addAll(listOf("education", "books"))
                "money" -> out.addAll(listOf("finance", "shopping", "business", "money"))
                "sports & health" -> out.addAll(listOf("health", "lifestyle", "entertainment"))
                "food" -> out.addAll(listOf("food", "lifestyle", "health", "entertainment"))
                "navigation", "navigation & maps" -> out.addAll(listOf("maps", "travel", "navigation", "tools"))
                "security", "system", "development", "theming", "automation", "xposed" -> {
                    out.addAll(listOf("tools", "security", lower))
                }
                else -> {
                    if (lower.contains("game")) out.addAll(listOf("games", "game", "entertainment"))
                }
            }
        }
        if (out.isEmpty()) out += DEFAULT_SLUG
        return out.toList()
    }
}

internal object FdroidDates {
    private val isoFormat: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
    }

    fun toIso(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return ""
        return isoFormat.get()?.format(Date(epochMillis)).orEmpty()
    }
}

internal object FdroidHtml {
    private val brTagRegex = Regex("(?i)<\\s*br\\s*/?\\s*>")
    private val blockEndRegex = Regex("(?i)</\\s*(p|div|li|h[1-6]|tr|blockquote)\\s*>")
    private val liStartRegex = Regex("(?i)<\\s*li[^>]*>")
    private val anyTagRegex = Regex("<[^>]+>")
    private val multiBlankLines = Regex("\n{3,}")
    private val multiSpaces = Regex("[ \t]{2,}")

    fun clean(raw: String): String {
        if (raw.isEmpty()) return ""
        if (!raw.contains('<') && !raw.contains('&')) return raw.trim()
        var s = raw
            .replace(brTagRegex, "\n")
            .replace(blockEndRegex, "\n\n")
            .replace(liStartRegex, "• ")
            .replace(anyTagRegex, "")
        s = s
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#x27;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
        s = s.replace(multiSpaces, " ")
        s = s.lines().joinToString("\n") { it.trim() }
        s = s.replace(multiBlankLines, "\n\n")
        return s.trim()
    }
}

/** Converts the streamed index into the store's models. */
fun FdroidIndex.toCatalog(repoUrl: String): CatalogResponse {
    val base = repoUrl.trimEnd('/')
    val releasesByPackage = packages
    val listings = ArrayList<StoreApp>(apps.size)
    val releases = ArrayList<StoreRelease>(apps.size)

    // Sort apps by most recently updated first (matching Droid-ify SortOrder.UPDATED)
    // so actively maintained apps surface at the top of shelves instead of
    // abandoned apps whose names start with digits or 'A'.
    val sortedApps = apps.sortedByDescending { meta ->
        val apk = releasesByPackage[meta.packageName]
        maxOf(meta.lastUpdated ?: 0L, apk?.added ?: 0L, meta.added ?: 0L)
    }

    for (meta in sortedApps) {
        val packageName = meta.packageName
        val apk = releasesByPackage[packageName] ?: continue
        val apkName = apk.apkName?.trim()?.removePrefix("/").orEmpty()
        if (apkName.isBlank()) continue

        val versionCode = apk.versionCode ?: 0L
        val localized = meta.localized
        val locale = meta.localizedLocale.ifBlank { "en-US" }
        val name = localized?.name?.trim().orEmpty()
            .ifBlank { meta.name?.trim().orEmpty() }
            .ifBlank { packageName.substringAfterLast('.') }
        val rawSummary = localized?.summary?.trim().orEmpty()
            .ifBlank { meta.summary?.trim().orEmpty() }
            .ifBlank { localized?.description?.trim().orEmpty() }
            .ifBlank { meta.description?.trim().orEmpty() }
        val rawDescription = localized?.description?.trim().orEmpty()
            .ifBlank { meta.description?.trim().orEmpty() }
            .ifBlank { rawSummary }
        val summary = FdroidHtml.clean(rawSummary)
        val description = FdroidHtml.clean(rawDescription).ifBlank { summary }

        val categories = meta.categories.mapNotNull { category ->
            category.trim().takeIf { it.isNotEmpty() }
        }
        val id = "fdroid:$packageName"
        val screenshots = localized?.phoneScreenshots
            ?.mapNotNull { shot -> fdroidScreenshotUrl(packageName, locale, shot, base) }
            .orEmpty()
        val featureGraphicUrl = fdroidFeatureGraphicUrl(meta, packageName, locale, base)
        val createdAtIso = FdroidDates.toIso(meta.added ?: apk.added)
        val updatedAtIso = FdroidDates.toIso(meta.lastUpdated ?: apk.added ?: meta.added)
        val changelog = localized?.whatsNew?.trim()?.takeIf { it.isNotEmpty() }
            ?.let(FdroidHtml::clean)
            .orEmpty()

        listings += StoreApp(
            id = id,
            packageName = packageName,
            name = name,
            developerName = meta.authorName?.trim().orEmpty()
                .ifBlank { meta.authorEmail?.trim().orEmpty() }
                .ifBlank { meta.license?.trim()?.takeIf { it.isNotEmpty() }?.let { "Open-source ($it)" }.orEmpty() }
                .ifBlank { "Open-source developer" },
            category = FdroidCategories.slugFor(categories),
            kind = if (FdroidCategories.isGame(categories)) "game" else "app",
            status = "published",
            shortDesc = summary,
            fullDesc = description,
            iconUrl = fdroidIconUrl(meta, packageName, locale, base),
            featureGraphicUrl = featureGraphicUrl,
            screenshots = screenshots,
            tags = FdroidCategories.tagsFor(categories),
            supportEmail = meta.authorEmail?.trim().orEmpty(),
            website = meta.webSite?.trim()?.takeIf { it.isNotBlank() }
                ?: meta.sourceCode?.trim()?.takeIf { it.isNotBlank() },
            privacyPolicyUrl = meta.sourceCode?.trim()?.takeIf { it.isNotBlank() },
            createdAt = createdAtIso,
            updatedAt = updatedAtIso,
            source = "F-Droid",
        )

        releases += StoreRelease(
            id = id,
            appId = id,
            versionCode = versionCode,
            versionName = apk.versionName?.trim().orEmpty().ifBlank { versionCode.toString() },
            apkProvider = "fdroid",
            apkKey = apkName,
            apkUrl = "$base/$apkName",
            apkSizeBytes = apk.size ?: 0L,
            apkSha256 = apk.hash?.trim().orEmpty().takeIf {
                it.isNotBlank() && apk.hashType.orEmpty().ifBlank { "sha256" }.equals("sha256", ignoreCase = true)
            },
            minSdk = apk.minSdkVersion ?: 1,
            targetSdk = apk.targetSdkVersion?.takeIf { it > 0 },
            permissions = apk.permissions,
            changelog = changelog,
            reviewStatus = "approved",
            isLive = true,
            createdAt = FdroidDates.toIso(apk.added ?: meta.lastUpdated ?: meta.added),
        )
    }

    return CatalogResponse(apps = listings, releases = releases, source = "F-Droid")
}

/**
 * The index's app icon is the versioned image in `icons-640/`. The localized
 * icon is a fallback hosted under the package's locale directory
 * (`<repoUrl>/<packageName>/<locale>/<icon>`), matching Droid-ify and official
 * F-Droid (`AppV1.kt`).
 */
private fun fdroidIconUrl(
    meta: FdroidApp,
    packageName: String,
    locale: String,
    repoUrl: String,
): String {
    val topIcon = meta.icon?.trim()?.takeIf { it.isNotBlank() && !it.endsWith(".xml", ignoreCase = true) }
    if (topIcon != null) {
        return when {
            topIcon.startsWith("http://") || topIcon.startsWith("https://") -> topIcon
            topIcon.startsWith("/") -> "$repoUrl$topIcon"
            else -> "$repoUrl/icons-640/$topIcon"
        }
    }
    val locIcon = meta.localized?.icon?.trim()?.takeIf { it.isNotBlank() } ?: return ""
    return when {
        locIcon.startsWith("http://") || locIcon.startsWith("https://") -> locIcon
        locIcon.startsWith("/") -> "$repoUrl$locIcon"
        locIcon.contains("/") -> "$repoUrl/$packageName/$locIcon"
        else -> "$repoUrl/$packageName/$locale/$locIcon"
    }
}

private fun fdroidFeatureGraphicUrl(
    meta: FdroidApp,
    packageName: String,
    locale: String,
    repoUrl: String,
): String? {
    val fg = meta.localized?.featureGraphic?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return when {
        fg.startsWith("http://") || fg.startsWith("https://") -> fg
        fg.startsWith("/") -> "$repoUrl$fg"
        fg.contains("/") -> "$repoUrl/$packageName/$fg"
        else -> "$repoUrl/$packageName/$locale/$fg"
    }
}

private fun fdroidScreenshotUrl(
    packageName: String,
    locale: String,
    shot: String,
    repoUrl: String,
): String? {
    val clean = shot.trim().takeIf { it.isNotEmpty() } ?: return null
    return when {
        clean.startsWith("http://") || clean.startsWith("https://") -> clean
        clean.startsWith("/") -> "$repoUrl$clean"
        clean.contains("/") -> "$repoUrl/$packageName/$clean"
        else -> "$repoUrl/$packageName/$locale/phoneScreenshots/$clean"
    }
}
