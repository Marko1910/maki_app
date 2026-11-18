package pe.ruma.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import pe.ruma.app.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Updates outside the Play Store: every signed APK is a GitHub Release of this (public) repo
 * tagged `android-v<versionCode>`, published by `scripts/release-android.sh`. On launch the app
 * asks GitHub for the newest one; if its versionCode beats ours it downloads the APK and hands it
 * to Android's installer, which keeps the account and data because the signing key is the same.
 */
object AppUpdater {
    private const val RELEASES = "https://api.github.com/repos/Marko1910/maki_app/releases?per_page=15"
    private const val TAG_PREFIX = "android-v"
    private const val APK_MIME = "application/vnd.android.package-archive"

    data class Update(val versionCode: Int, val title: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

    @Serializable
    data class GhAsset(val name: String, @SerialName("browser_download_url") val url: String, val size: Long = 0)

    @Serializable
    data class GhRelease(
        @SerialName("tag_name") val tag: String,
        val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GhAsset> = emptyList(),
    )

    // Unauthenticated GitHub API: 60 requests/hour per IP, one per launch is far below it.
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** The newest published Android release above [current], or null. Pure, so it is unit-tested. */
    fun pickUpdate(releases: List<GhRelease>, current: Int): Update? = releases
        .asSequence()
        .filter { !it.draft && !it.prerelease && it.tag.startsWith(TAG_PREFIX) }
        .mapNotNull { r ->
            val code = r.tag.removePrefix(TAG_PREFIX).toIntOrNull() ?: return@mapNotNull null
            val apk = r.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return@mapNotNull null
            Update(code, r.name?.takeIf { it.isNotBlank() } ?: "Versión $code", r.body.orEmpty().trim(), apk.url, apk.size)
        }
        .filter { it.versionCode > current }
        .maxByOrNull { it.versionCode }

    /** Null when up to date or offline: an update check never gets in the way of the app. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(RELEASES)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Ruma-Android/${BuildConfig.VERSION_CODE}")
                .build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val list = RumaApi.json.decodeFromString<List<GhRelease>>(res.body!!.string())
                pickUpdate(list, BuildConfig.VERSION_CODE)
            }
        }.getOrNull()
    }

    /** Downloads the APK into the cache (GitHub redirects to its CDN; OkHttp follows). */
    suspend fun download(context: Context, update: Update, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // old downloads
        val out = File(dir, "ruma-v${update.versionCode}.apk")
        val req = Request.Builder().url(update.apkUrl).header("User-Agent", "Ruma-Android").build()
        http.newCall(req).execute().use { res ->
            check(res.isSuccessful) { "No se pudo descargar (${res.code})" }
            val body = res.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        out
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
