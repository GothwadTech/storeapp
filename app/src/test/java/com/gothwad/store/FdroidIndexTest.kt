package com.gothwad.store

import com.gothwad.store.data.remote.FdroidCategories
import com.gothwad.store.data.remote.FdroidIndex
import com.gothwad.store.data.remote.FdroidIndexParser
import com.gothwad.store.data.remote.toCatalog
import com.squareup.moshi.JsonReader
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-Droid's `index-v1.json` is the only upstream catalogue the app reads, so
 * its exact shape is pinned here.
 *
 * The important detail: `apps` is an **array** of app objects while `packages`
 * is a map of package name -> APK list, and the human readable name usually
 * lives inside `localized["en-US"]`. Reading `apps` as a JSON object silently
 * produced an empty catalogue, which is why no F-Droid app ever showed up.
 *
 * The payload below is trimmed from the real `https://f-droid.org/repo/index-v1.json`.
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
                  "icon": "icon_FOzSYq6etfsaWRiMc7bx-8vLVKtsug1dmhT9NvxRj9w=.png"
                }
              }
            },
            {
              "packageName": "org.fdroid.fdroid",
              "name": "F-Droid",
              "summary": "The official F-Droid Free Software repository.",
              "description": "F-Droid is an installable app catalogue.",
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
                "versionName": "1.1.2"
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
    }

    @Test
    fun `localized name wins over the package name`() {
        val catalog = parse().toCatalog(repoUrl = "https://f-droid.org/repo")
        val app = catalog.apps.first { it.packageName == "com.terokarvinen.x54ask" }
        assertEquals("0x54ask", app.name)
        assertEquals("fdroid:com.terokarvinen.x54ask", app.id)
        assertEquals("Todo.txt manager. Offline, works with Syncthing.", app.shortDesc)
        assertEquals("0x54ask is a todo.txt manager.", app.fullDesc)
        assertEquals("Tero Karvinen", app.developerName)
        assertEquals("published", app.status)
        assertEquals("F-Droid", app.source)
    }

    @Test
    fun `f-droid categories map onto kiwi store slugs`() {
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
}
