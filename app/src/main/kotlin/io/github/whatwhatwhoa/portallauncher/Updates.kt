package io.github.whatwhatwhoa.portallauncher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A release on GitHub: its version (the tag without its "v") and the APK attached to it. */
class Release(val version: String, val apk: String, val size: Long)

/**
 * Updates from the project's GitHub releases (F18): the latest release's tag against our own
 * version, its APK downloaded, checked and handed to Android's package installer. Nothing runs
 * in the background; the Updates row in Launcher Settings asks when the panel opens.
 */
class Updates(private val context: Context) {
    private val dir = File(context.cacheDir, "update")

    /**
     * The latest release with an APK, or null when none is published: GitHub answers 404 for
     * that, and for a private repo. Whether it is newer is [isNewer]'s.
     */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val conn = connect(BuildConfig.UPDATE_URL).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        val latest = try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> JSONObject(conn.inputStream.bufferedReader().readText())
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
        // An update left from before has been installed or isn't wanted any more.
        dir.deleteRecursively()
        latest ?: return@withContext null
        val version = latest.getString("tag_name").removePrefix("v")
        val assets = latest.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@withContext null
        Release(version, apk.getString("browser_download_url"), apk.optLong("size"))
    }

    /** The release's APK in the cache, reporting each whole percent as it comes. */
    suspend fun download(release: Release, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        dir.deleteRecursively()
        dir.mkdirs()
        val to = File(dir, "portal-${release.version}.apk")
        val part = File(dir, "${to.name}.part")
        // GitHub sends the APK from another host; a redirect within https is followed.
        val conn = connect(release.apk)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
            var done = 0L
            var shown = -1
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        if (percent != shown) {
                            shown = percent
                            withContext(Dispatchers.Main) { progress(percent) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!part.renameTo(to)) throw IOException("Couldn't save $to")
        to
    }

    /**
     * Why [apk] can't replace this install, or null when it can: it must be this app, newer, and
     * signed with the same key. Android refuses the rest anyway, but only after the install has
     * started, and a debug build (signed with the debug key) never takes a release.
     */
    fun refusal(apk: File): String? {
        val pm = context.packageManager
        val theirs = archiveInfo(pm, apk) ?: return "Not an app"
        val ours = installedInfo(pm)
        if (theirs.packageName != ours.packageName) return "Not this app"
        if (theirs.longVersionCode <= ours.longVersionCode) return "Not newer"
        val signers = { info: PackageInfo -> info.signingInfo?.apkContentsSigners?.toSet().orEmpty() }
        if (signers(theirs).isEmpty() || signers(theirs) != signers(ours)) return "Signed differently"
        return null
    }

    /** Whether Android lets us install, the one-time "Install unknown apps" grant. */
    fun mayInstall() = context.packageManager.canRequestPackageInstalls()

    /**
     * Hands [apk] to Android. An app updating itself needs no confirm on Android 12 and later
     * (UPDATE_PACKAGES_WITHOUT_USER_ACTION); if Android asks anyway, [UpdateReceiver] shows its
     * dialog. Once the update is in, Android stops this process and starts the new Home.
     */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("portal.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // Mutable: the installer adds its status to the intent.
            val reply = PendingIntent.getBroadcast(
                context, id, Intent(context, UpdateReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(reply.intentSender)
        }
    }

    private fun connect(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
    }

    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    private fun installedInfo(pm: PackageManager): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    companion object {
        private const val TIMEOUT_MS = 15_000

        /** Told when an install fails; the Updates row shows it. */
        var failed: ((String) -> Unit)? = null

        /** Whether dotted version [theirs] is past [ours]; a suffix like "-check" is ignored. */
        fun isNewer(theirs: String, ours: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(theirs)
            val b = parts(ours)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

/** The package installer's answer to [Updates.install]. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            // Success ends this process; nothing to show.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updates.failed?.invoke("Cancelled")
            else -> Updates.failed?.invoke("Install failed")
        }
    }
}
