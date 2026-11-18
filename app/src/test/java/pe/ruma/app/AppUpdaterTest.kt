package pe.ruma.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.ruma.app.data.AppUpdater
import pe.ruma.app.data.AppUpdater.GhAsset
import pe.ruma.app.data.AppUpdater.GhRelease

class AppUpdaterTest {
    private fun rel(tag: String, apk: Boolean = true, draft: Boolean = false, pre: Boolean = false) =
        GhRelease(tag, "R $tag", "notas", draft, pre, if (apk) listOf(GhAsset("Ruma.apk", "https://x/$tag.apk", 10)) else emptyList())

    @Test fun picksNewestAndroidReleaseAboveCurrent() {
        val list = listOf(rel("android-v3"), rel("pwa-v9"), rel("android-v5", draft = true), rel("android-v4", pre = true), rel("android-v2"))
        assertEquals(3, AppUpdater.pickUpdate(list, current = 1)?.versionCode)
        assertEquals("https://x/android-v3.apk", AppUpdater.pickUpdate(list, current = 1)?.apkUrl)
    }

    @Test fun nothingWhenUpToDateOrNoApk() {
        assertNull(AppUpdater.pickUpdate(listOf(rel("android-v2")), current = 2))
        assertNull(AppUpdater.pickUpdate(listOf(rel("android-v3", apk = false)), current = 2))
        assertNull(AppUpdater.pickUpdate(listOf(rel("android-vX")), current = 0))
    }

    @Test fun parsesGithubJson() {
        val json = """[{"tag_name":"android-v2","name":"Ruma 1.2","body":"Arreglos","draft":false,"prerelease":false,
            "assets":[{"name":"Ruma-v2.apk","browser_download_url":"https://github.com/a.apk","size":18094266,"id":1}],"id":9}]"""
        val list = pe.ruma.app.data.RumaApi.json.decodeFromString<List<GhRelease>>(json)
        val u = AppUpdater.pickUpdate(list, current = 1)!!
        assertEquals("Ruma 1.2", u.title); assertEquals("Arreglos", u.notes); assertEquals(18094266L, u.sizeBytes)
    }
}
