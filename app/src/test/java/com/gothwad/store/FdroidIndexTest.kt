package com.gothwad.store

import com.gothwad.store.data.remote.FdroidCategories
import com.gothwad.store.data.remote.FdroidIndex
import com.gothwad.store.data.remote.FdroidIndexParser
import com.gothwad.store.data.remote.StoreRepository
import com.gothwad.store.data.remote.toCatalog
import com.squareup.moshi.JsonReader
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * F-Droid's `index-v1.jar` / `index-v1.json` and `index-v2.json` schemas are
 * pinned here.
 *
 * Key details tested:
 * 1. `index-v1.jar` is a ZIP/JAR archive containing `index-v1.json` (which is
 *    what Droid-ify and the official F-Droid client download).
 * 2. `apps` in v1 is an **array** of app objects while `packages` is a map of
 *    package name -> APK list, and the human-readable name/summary/icon often
 *    live inside `localized["en-US"]` (or another locale).
 * 3. Multi-ABI packages (`nativecode`) select the highest versionCode
 *    compatible with the device's `Build.SUPPORTED_ABIS` and `SDK_INT`.
 * 4. `toCatalog` sorts by `lastUpdated` descending, cleans HTML from
 *    descriptions, and maps all F-Droid categories + aliases so every category
 *    screen and shelf is populated.
 */
class FdroidIndexTest {

    private val indexPayload = """
        {
          "repo": { "timestamp": 1792190113856, "version": 30000, "name": "F-Droid" },
          "requests": { "install": [], "uninstall": [] },
          "apps": [
            {
              "packageName": "com.terokarvinen.x54ask",
              "authorName": "Tero Karvinen",
              "authorWebSite": "https://terokarvinen.com/",
              "categories": ["Calendar & Agenda", "Note", "Task"],
              "suggestedVersionName": "1.1.2",
              "suggestedVersionCode": "1010200",
              "license": "GPL-3.0-or-later",
              "added": 1788427366142,
              "icon": "com.terokarvinen.x54ask.1010200.png",
              "lastUpdated": 1788427366142,
              "localized": {
                "en-US": {
                  "name": "0x54ask",
                  "summary": "Todo.txt manager. Offline, works with Syncthing.",
                  "description": "0x54ask is a todo.txt manager.",
                  "icon": "icon_FOzSYq6etfsaWRiMc7bx-8vLVKtsug1dmhT9NvxRj9w=.png",
                  "phoneScreenshots": ["shot1.png", "shot2.png"]
                }
              }
            },
            {
              "packageName": "org.fdroid.fdroid",
              "name": "F-Droid",
              "summary": "The official F-Droid Free Software repository.",
              "description": "<p>F-Droid is an <b>installable</b> app catalogue.</p>",
              "license": "GPL-2.0-or-later",
              "categories": ["System"],
              "icon": "org.fdroid.fdroid.1017010.png",
              "webSite": "https://f-droid.org",
              "added": 1457568000000,
              "lastUpdated": 1498176000000
            },
            {
              "packageName": "com.example.oldgame",
              "name": "Retro Racer",
              "summary": "A retro racing game.",
              "categories": ["Games"],
              "icon": "com.example.oldgame.1.png",
              "added": 1,
              "lastUpdated": 1
            },
            {
              "packageName": "com.example.toonew",
              "name": "Too New",
              "categories": ["Internet"],
              "icon": "com.example.toonew.1.png"
            },
            {
              "packageName": "com.example.party",
              "name": "Party Game",
              "summary": "A party game.",
              "categories": ["Party Game", "Science & Education"],
              "icon": "com.example.party.1.png"
            }
          ],
          "packages": {
            "com.terokarvinen.x54ask": [
              {
                "apkName": "com.terokarvinen.x54ask_1010200.apk",
                "hash": "aaaa",
                "hashType": "sha256",
                "minSdkVersion": 29,
                "size": 12345,
                "targetSdkVersion": 36,
                "versionCode": 1010200,
                "versionName": "1.1.2",
                "uses-permission": [["android.permission.INTERNET", null]]
              },
              {
                "apkName": "com.terokarvinen.x54ask_1000100.apk",
                "hash": "bbbb",
                "hashType": "sha256",
                "minSdkVersion": 21,
                "size": 12000,
                "targetSdkVersion": 33,
                "versionCode": 1000100,
                "versionName": "1.0.1"
              }
            ],
            "org.fdroid.fdroid": [
              {
                "apkName": "org.fdroid.fdroid_1017010.apk",
                "hash": "cccc",
                "hashType": "sha256",
                "minSdkVersion": 21,
                "size": 9999,
                "targetSdkVersion": 34,
                "versionCode": 1017010,
                "versionName": "1.0.17"
              }
            ],
            "com.example.oldgame": [
              {
                "apkName": "com.example.oldgame_1.apk",
                "hash": "dddd",
                "hashType": "sha256",
                "minSdkVersion": 21,
                "size": 5555,
                "targetSdkVersion": 30,
                "versionCode": 1,
                "versionName": "1.0"
              }
            ],
            "com.example.toonew": [
              {
                "apkName": "com.example.toonew_1.apk",
                "hash": "eeee",
                "hashType": "sha256",
                "minSdkVersion": 40,
                "size": 4444,
                "targetSdkVersion": 40,
                "versionCode": 1,
                "versionName": "1.0"
              }
            ]
          }
        }
    """.trimIndent()

    private fun parse(sdkInt: Int = 34): FdroidIndex =
        FdroidIndexParser.parse(JsonReader.of(Buffer().writeUtf8(indexPayload)), sdkInt)

    @Test(expected = IllegalStateException::class)
    fun `apps encoded as an object is rejected instead of silently becoming empty`() {
        val wrongShape = """{"apps": {}, "packages": {}}"""
        FdroidIndexParser.parse(JsonReader.of(Buffer().writeUtf8(wrongShape)), sdkInt = 34)
    }

    @Test
    fun `apps is an array and every entry reaches the catalogue`() {
        val index = parse()
        assertEquals(5, index.apps.size)
        // The parser retains only a single compatible APK per package; the
        // API-40 app is excluded on this API-34 test device.
        assertEquals(3, index.packages.size)

        val catalog = index.toCatalog(repoUrl = "https://f-droid.org/repo")
        // "com.example.toonew" needs API 40 and "com.example.party" has no APK.
        assertEquals(3, catalog.apps.size)
        assertEquals("F-Droid", catalog.source)
        // Sorted by lastUpdated descending: 0x54ask (newest) -> F-Droid -> Retro Racer (oldest)
        assertEquals("com.terokarvinen.x54ask", catalog.apps[0].packageName)
        assertEquals("org.fdroid.fdroid", catalog.apps[1].packageName)
        assertEquals("com.example.oldgame", catalog.apps[2].packageName)
    }

    @Test
    fun `localized name wins over the package name and html is cleaned`() {
        val catalog = parse().toCatalog(repoUrl = "https://f-droid.org/repo")
        val app = catalog.apps.first { it.packageName == "com.terokarvinen.x54ask" }
        assertEquals("0x54ask", app.name)
        assertEquals("fdroid:com.terokarvinen.x54ask", app.id)
        assertEquals("Todo.txt manager. Offline, works with Syncthing.", app.shortDesc)
        assertEquals("0x54ask is a todo.txt manager.", app.fullDesc)
        assertEquals("Tero Karvinen", app.developerName)
        assertEquals("published", app.status)
        assertEquals("F-Droid", app.source)
        assertEquals(2, app.screenshots.size)
        assertEquals(
            "https://f-droid.org/repo/com.terokarvinen.x54ask/en-US/phoneScreenshots/shot1.png",
            app.screenshots[0],
        )

        val fdroidApp = catalog.apps.first { it.packageName == "org.fdroid.fdroid" }
        assertEquals("F-Droid is an installable app catalogue.", fdroidApp.fullDesc)
        assertTrue(fdroidApp.updatedAt.isNotBlank())
    }

    @Test
    fun `f-droid categories map onto kiwi store slugs and aliases`() {
        val catalog = parse().toCatalog(repoUrl = "https://f-droid.org/repo")
        assertEquals(
            "productivity",
            catalog.apps.first { it.packageName == "com.terokarvinen.x54ask" }.category,
        )
        assertEquals("tools", catalog.apps.first { it.packageName == "org.fdroid.fdroid" }.category)
        // "Games" is a category in F-Droid but a kind in Kiwi Store.
        val game = catalog.apps.first { it.packageName == "com.example.oldgame" }
        assertEquals("game", game.kind)
        assertEquals("entertainment", game.category)

        val repo = StoreRepository()
        assertTrue(repo.categoryMatches(game, "games"))
        assertTrue(repo.categoryMatches(game, "entertainment"))
    }

    @Test
    fun `category lookup knows every seeded slug`() {
        assertEquals("browser", FdroidCategories.slug("Internet"))
        assertEquals("communication", FdroidCategories.slug("Phone & SMS"))
        assertEquals("education", FdroidCategories.slug("Science & Education"))
        assertEquals("music", FdroidCategories.slug("Music & Audio"))
        assertEquals("photo", FdroidCategories.slug("Photography"))
        assertEquals("entertainment", FdroidCategories.slug("Party Game"))
        assertTrue(FdroidCategories.isGame(listOf("Party Game")))
        assertEquals(null, FdroidCategories.slug("Something Kiwi Store does not know"))
        assertEquals("tools", FdroidCategories.slugFor(listOf("Something Kiwi Store does not know")))
        assertEquals("tools", FdroidCategories.slugFor(emptyList()))
    }

    @Test
    fun `the newest build that runs on the device wins`() {
        val modern = parse().toCatalog(repoUrl = "https://f-droid.org/repo")
        val release = modern.releases.first { it.appId == "fdroid:com.terokarvinen.x54ask" }
        assertEquals(1010200L, release.versionCode)
        assertEquals("1.1.2", release.versionName)
        assertEquals("fdroid", release.apkProvider)
        assertEquals("https://f-droid.org/repo/com.terokarvinen.x54ask_1010200.apk", release.apkUrl)
        assertEquals(12345L, release.apkSizeBytes)
        assertEquals("aaaa", release.apkSha256)
        assertEquals(29, release.minSdk!!)
        assertEquals(36, release.targetSdk!!)
        assertEquals(listOf("android.permission.INTERNET"), release.permissions)
        assertTrue(release.isLive)
        assertEquals("approved", release.reviewStatus)

        // On API 24 only the older build still runs.
        val legacy = parse(sdkInt = 24).toCatalog(repoUrl = "https://f-droid.org/repo")
        val older = legacy.releases.first { it.appId == "fdroid:com.terokarvinen.x54ask" }
        assertEquals(1000100L, older.versionCode)
        assertEquals("https://f-droid.org/repo/com.terokarvinen.x54ask_1000100.apk", older.apkUrl)
    }

    @Test
    fun `icons point at the f-droid repository`() {
        val catalog = parse().toCatalog(repoUrl = "https://f-droid.org/repo")
        assertEquals(
            "https://f-droid.org/repo/icons-640/com.terokarvinen.x54ask.1010200.png",
            catalog.apps.first { it.packageName == "com.terokarvinen.x54ask" }.iconUrl,
        )
        assertEquals(
            "https://f-droid.org/repo/icons-640/org.fdroid.fdroid.1017010.png",
            catalog.apps.first { it.packageName == "org.fdroid.fdroid" }.iconUrl,
        )
    }

    @Test
    fun `an app without a published apk never reaches the store`() {
        val index = parse()
        val withoutApk = index.copy(packages = index.packages - "com.example.oldgame")
        val catalog = withoutApk.toCatalog(repoUrl = "https://f-droid.org/repo")
        assertNotNull(catalog)
        assertTrue(catalog.apps.none { it.packageName == "com.example.oldgame" })
    }

    @Test
    fun `parses index-v1 jar archive via parseSource and parseFile`() {
        val jarBytes = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                zos.write("Manifest-Version: 1.0\n".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("index-v1.json"))
                zos.write(indexPayload.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }.toByteArray()

        val fromStream = FdroidIndexParser.parseSource(
            source = Buffer().write(jarBytes),
            sdkInt = 34,
        ).toCatalog("https://f-droid.org/repo")
        assertEquals(3, fromStream.apps.size)

        val tempJar = File.createTempFile("fdroid_test_", ".jar")
        try {
            tempJar.writeBytes(jarBytes)
            val fromFile = FdroidIndexParser.parseFile(
                file = tempJar,
                sdkInt = 34,
            ).toCatalog("https://f-droid.org/repo")
            assertEquals(3, fromFile.apps.size)
        } finally {
            tempJar.delete()
        }
    }

    @Test
    fun `nativecode abi filtering skips incompatible x86_64 builds on arm64 devices`() {
        val multiAbiPayload = """
            {
              "apps": [
                {
                  "packageName": "com.junkfood.seal",
                  "name": "Seal",
                  "summary": "Video/Audio Downloader",
                  "categories": ["Multimedia"]
                }
              ],
              "packages": {
                "com.junkfood.seal": [
                  {
                    "apkName": "com.junkfood.seal_12014.apk",
                    "versionCode": 12014,
                    "versionName": "1.12.1",
                    "minSdkVersion": 24,
                    "nativecode": ["x86_64"]
                  },
                  {
                    "apkName": "com.junkfood.seal_12012.apk",
                    "versionCode": 12012,
                    "versionName": "1.12.1",
                    "minSdkVersion": 24,
                    "nativecode": ["arm64-v8a"]
                  }
                ]
              }
            }
        """.trimIndent()

        val index = FdroidIndexParser.parse(
            reader = JsonReader.of(Buffer().writeUtf8(multiAbiPayload)),
            sdkInt = 34,
            supportedAbis = setOf("arm64-v8a", "armeabi-v7a"),
        )
        val apk = index.packages["com.junkfood.seal"]
        assertNotNull(apk)
        assertEquals(12012L, apk!!.versionCode)
        assertEquals("com.junkfood.seal_12012.apk", apk.apkName)
    }

    @Test
    fun `parses index-v2 schema with localized metadata and versions`() {
        val v2Payload = """
            {
              "repo": { "name": { "en-US": "F-Droid" }, "timestamp": 1792190113856 },
              "packages": {
                "org.videolan.vlc": {
                  "metadata": {
                    "name": { "en-US": "VLC" },
                    "summary": { "en-US": "The best open-source media player" },
                    "description": { "en-US": "Plays everything." },
                    "categories": ["Multimedia"],
                    "icon": { "en-US": { "name": "/org.videolan.vlc/en-US/icon.png" } },
                    "added": 1600000000000,
                    "lastUpdated": 1700000000000
                  },
                  "versions": {
                    "sha256_1": {
                      "added": 1700000000000,
                      "file": {
                        "name": "/org.videolan.vlc_13050304.apk",
                        "sha256": "abcd1234",
                        "size": 42000000
                      },
                      "manifest": {
                        "versionName": "3.5.3",
                        "versionCode": 13050304,
                        "usesSdk": { "minSdkVersion": 21, "targetSdkVersion": 33 },
                        "nativecode": ["arm64-v8a"]
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val catalog = FdroidIndexParser.parse(
            reader = JsonReader.of(Buffer().writeUtf8(v2Payload)),
            sdkInt = 34,
            supportedAbis = setOf("arm64-v8a"),
        ).toCatalog("https://f-droid.org/repo")

        assertEquals(1, catalog.apps.size)
        val vlc = catalog.apps.first()
        assertEquals("VLC", vlc.name)
        assertEquals("https://f-droid.org/repo/org.videolan.vlc/en-US/icon.png", vlc.iconUrl)
        val rel = catalog.releases.first()
        assertEquals("https://f-droid.org/repo/org.videolan.vlc_13050304.apk", rel.apkUrl)
        assertEquals(13050304L, rel.versionCode)
    }
}
