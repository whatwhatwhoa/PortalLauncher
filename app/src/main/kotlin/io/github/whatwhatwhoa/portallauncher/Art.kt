package io.github.whatwhatwhoa.portallauncher

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.OnTrimMemoryProvider
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/** A SteamGridDB game, the source of an app's art. */
class ArtMatch(val gameId: Int, val name: String, val year: Int?) {
    val label get() = if (year != null) "$name ($year)" else name
}

/**
 * One picture the owner can choose for a slot: [url] is the full size that gets downloaded,
 * [thumb] a small copy for the picker. The url doubles as its identity.
 */
class ArtImage(val url: String, val thumb: String, val width: Int, val height: Int)

/** What is known about an app's art. Stored per package, so every profile's copy shares it. */
sealed interface ArtRecord {
    class Matched(val match: ArtMatch) : ArtRecord
    /** The owner chose the app's own icon: no lookup ever again. */
    data object Icon : ArtRecord
    /** Looked up automatically and nothing matched the label closely enough; the owner can still pick. */
    data object NoMatch : ArtRecord
    /** No game, but the owner gave it images from the gallery: no lookup ever again. */
    data object Own : ArtRecord
}

/**
 * [Scene] is a 1920×1080 picture that fills Home behind everything: one of the game's Steam store
 * screenshots (SteamGridDB has nothing 16:9).
 */
enum class ArtKind(val suffix: String) { Cover("cover"), Hero("hero"), Scene("scene") }

/**
 * How fetching a game's art went: [NoImages] is SteamGridDB having none for it, [Failed] is not
 * reaching it (offline, an HTTP error, a write that failed), which is tried again later and never
 * taken for "no match".
 */
enum class ArtFetch { Done, NoImages, Failed }

/**
 * Art from SteamGridDB: a square cover and a 1920 px wide hero per app, downloaded once into the
 * app's files and never upscaled (only sizes at least as big as they are shown are asked for).
 * Favorites and apps Android marks as games are looked up by their label and taken only on a
 * close match; anything else waits for the owner (Triangle → Art…). A matched game also gets a scene
 * from Steam when it is sold there, which Home shows only once the owner chooses it. Lookups
 * need the owner's own SteamGridDB [key] (Launcher Settings → Art); without one, only gallery
 * images work.
 */
class Art(context: Context, private val scope: CoroutineScope, key: String) {
    var key by mutableStateOf(key)
    val hasKey get() = key.isNotBlank()

    private val dir = File(context.filesDir, "art").apply { mkdirs() }
    private val resolver = context.contentResolver
    private val sp = context.getSharedPreferences("art", Context.MODE_PRIVATE)
    private val writing = Mutex()
    private val looked = HashSet<String>()
    // "pkg.kind" → the url of the image the owner picked for that slot, for the picker's dot.
    private val picks = context.getSharedPreferences("picks", Context.MODE_PRIVATE)
    private val thumbs = LruCache<String, ImageBitmap>(THUMB_CACHE)
    // Package → the Steam app its scene came from, or NO_SCENE: either way it isn't asked again.
    // "pkg.url" → the shot the score chose, so the picker can mark it.
    private val scenes = context.getSharedPreferences("scenes", Context.MODE_PRIVATE)
    private val sceneLooked = HashSet<String>()
    private val refetched = HashSet<String>()
    // Matched apps whose images didn't all come (a download failed), fetched when the network is
    // back or at the next start. On disk for that, in a file of its own outside the setup: after a
    // restore every image is fetched again anyway.
    private val pending = context.getSharedPreferences("art.pending", Context.MODE_PRIVATE)
    private val unfinished = HashSet<String>(pending.getStringSet(PENDING, null).orEmpty())
    // Read for Fetch art when the network comes back, since only the root knows it otherwise.
    private val launcherPrefs = Prefs(context)

    /** Package → record. Not state itself: each change bumps the package's [revision], which is. */
    private val records = HashMap<String, ArtRecord>().apply {
        sp.all.forEach { (pkg, value) -> decode(value as? String)?.let { put(pkg, it) } }
    }

    // Bumped on every change, so a cover replaced under the same file name is decoded again. One
    // state per package, so a change redraws that app's art, not every tile on Home.
    private val revisions = HashMap<String, MutableIntState>()

    // The images on disk by file name, listed once here and kept as files come and go, so drawing
    // never waits on the disk. Downloads land on the IO threads, hence the concurrent set.
    private val present: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply { dir.list()?.let { addAll(it) } }

    // Decoded art, so a tile composed again (a category turned, the layer's preview) shows its
    // cover on its first frame instead of decoding it again: a few dozen covers, a few heroes.
    private val covers = bitmaps(COVER_CACHE)
    private val wides = bitmaps(WIDE_CACHE)

    // Whether a lookup, scene or refetch failed since the network last came back, and for which
    // apps, so its return can run them again (lookUp skips what already succeeded).
    private var failedOffline = false
    private var wanted: List<AppEntry> = emptyList()

    init {
        // Hidden behind a game, the heroes and scenes go first; under real pressure, everything.
        (context as? OnTrimMemoryProvider)?.addOnTrimMemoryListener { level ->
            if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) wides.evictAll()
            if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) covers.evictAll()
        }
        // Once per return of the network, after it has settled: a flaky join reports several.
        scope.launch {
            networkReturns(context.applicationContext).collectLatest {
                delay(NETWORK_SETTLE_MS)
                if (failedOffline) {
                    failedOffline = false
                    if (launcherPrefs.fetchArt) lookUp(wanted)
                    // A game chosen in Art… gets the images that didn't come, whatever Fetch art says.
                    unfinished.toList().forEach(::finish)
                }
            }
        }
    }

    fun record(pkg: String): ArtRecord? {
        revision(pkg)
        return records[pkg]
    }

    fun revision(pkg: String) = revisionOf(pkg).intValue

    private fun revisionOf(pkg: String) = revisions.getOrPut(pkg) { mutableIntStateOf(0) }

    private fun bump(pkg: String) {
        revisionOf(pkg).intValue++
    }

    fun file(pkg: String, kind: ArtKind): File? {
        val record = record(pkg)
        val name = "$pkg.${kind.suffix}"
        return if ((record is ArtRecord.Matched || record == ArtRecord.Own) && name in present) File(dir, name) else null
    }

    /** [file] decoded at [maxPx] for this [revision], when it is already in memory. */
    fun cached(file: File, kind: ArtKind, maxPx: Int, revision: Int): ImageBitmap? = cacheOf(kind).get(cacheKey(file, maxPx, revision))

    /**
     * [pkg]'s [file] decoded at [maxPx], from memory or else from disk. Covers decode two at a
     * time; a hero or a scene waits behind the one decoding, so a direction held along the row
     * runs one full-width decode at a time and the steps passed meanwhile drop out unstarted
     * (ImageDecoder can't be stopped once it runs). A file that won't decode counts as missing.
     */
    suspend fun bitmap(pkg: String, file: File, kind: ArtKind, maxPx: Int, revision: Int): ImageBitmap? {
        val key = cacheKey(file, maxPx, revision)
        val cache = cacheOf(kind)
        cache.get(key)?.let { return it }
        val bitmap = withContext(if (kind == ArtKind.Cover) COVER_LANE else WIDE_LANE) {
            // Kept even when whoever asked has moved on, so stepping back finds it.
            cache.get(key) ?: decode(file, maxPx)?.also { cache.put(key, it) }
        }
        if (bitmap == null && present.remove(file.name)) bump(pkg)
        return bitmap
    }

    private fun cacheOf(kind: ArtKind) = if (kind == ArtKind.Cover) covers else wides

    private fun cacheKey(file: File, maxPx: Int, revision: Int) = "${file.name}@$maxPx#$revision"

    /**
     * Looks up every app here that has no record yet, one at a time, once per launcher start; a
     * matched app is finished ([finish]). What failed for the network runs again when it comes
     * back.
     */
    fun lookUp(apps: List<AppEntry>) {
        wanted = apps
        val unique = apps.distinctBy { it.component.packageName }
        unique.forEach { finish(it.component.packageName) }
        if (!hasKey) return
        val todo = unique.filter { it.component.packageName !in records && looked.add(it.component.packageName) }
        if (todo.isEmpty()) return
        scope.launch {
            for (app in todo) {
                val pkg = app.component.packageName
                val match = try {
                    search(app.label).firstOrNull { closeMatch(app.label, it.name) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Offline or the service is down: again when the network is back, or next start.
                    Log.w(TAG, "Art lookup for ${app.label} failed: $e", e)
                    looked.remove(pkg)
                    failedOffline = true
                    continue
                }
                if (pkg in records) continue
                // Only a lookup that truly found nothing is "no match". A failed download keeps
                // the match once SteamGridDB has listed its images ([use]); a failure before that
                // leaves the app unrecorded, so the next lookup tries it whole.
                when (if (match == null) ArtFetch.NoImages else use(pkg, match)) {
                    ArtFetch.NoImages -> save(pkg, ArtRecord.NoMatch)
                    ArtFetch.Failed -> looked.remove(pkg)
                    ArtFetch.Done -> Unit
                }
            }
        }
    }

    /**
     * Whether SteamGridDB takes [candidate] as a key: one search with it. Throws when the service
     * can't be reached, so offline isn't mistaken for a wrong key.
     */
    suspend fun accepts(candidate: String): Boolean = withContext(Dispatchers.IO) {
        val c = connect("$BASE/search/autocomplete/portal").apply { setRequestProperty("Authorization", "Bearer $candidate") }
        try {
            when (c.responseCode) {
                HttpURLConnection.HTTP_OK -> true
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> false
                else -> throw IOException("HTTP ${c.responseCode}")
            }
        } finally {
            c.disconnect()
        }
    }

    /** SteamGridDB's autocomplete: the games whose names fit [term], best first. */
    suspend fun search(term: String): List<ArtMatch> {
        val data = api("/search/autocomplete/${Uri.encode(term.trim())}").getJSONArray("data")
        return data.objects().map {
            ArtMatch(it.getInt("id"), it.getString("name"), it.optLong("release_date").takeIf { s -> s > 0 }?.let(::year))
        }
    }

    /**
     * Downloads [match]'s cover and hero and makes it [pkg]'s art, then looks for its scene.
     * Once SteamGridDB has listed images for it, a failed download still makes it the app's game,
     * and what didn't come is fetched when the network is back ([finish]). A cancel is passed on,
     * not taken for a failure.
     */
    suspend fun use(pkg: String, match: ArtMatch): ArtFetch = writing.withLock {
        val before = (records[pkg] as? ArtRecord.Matched)?.match?.gameId
        val fetched = mutableListOf<ArtKind>()
        var listed = false
        try {
            val cover = best(ArtKind.Cover, match.gameId)
            val hero = best(ArtKind.Hero, match.gameId)
            if (cover == null && hero == null) return ArtFetch.NoImages
            listed = true
            download(cover, File(dir, "$pkg.${ArtKind.Cover.suffix}"))
            fetched += ArtKind.Cover
            download(hero, File(dir, "$pkg.${ArtKind.Hero.suffix}"))
            fetched += ArtKind.Hero
            adopt(pkg, match, before, fetched)
            unfinish(pkg, false)
            ArtFetch.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Fetching art for $pkg failed: $e", e)
            failedOffline = true
            if (listed) {
                adopt(pkg, match, before, fetched)
                unfinish(pkg, true)
                // Even when this start already fetched its images once.
                refetched.remove(pkg)
            }
            ArtFetch.Failed
        }
    }

    /**
     * Makes [match] [pkg]'s game, its [fetched] slots new. Another game's scene and picks go, and
     * so do the slots not fetched, which were another game's or the gallery's: [refetch] fills
     * them. The same game's stay.
     */
    private fun adopt(pkg: String, match: ArtMatch, before: Int?, fetched: List<ArtKind>) {
        val changed = before != match.gameId
        if (changed) {
            delete(sceneFile(pkg))
            scenes.edit { remove(pkg); remove("$pkg.url") }
            picks.edit { remove(pickKey(pkg, ArtKind.Scene)); remove(wideKey(pkg)) }
            // A new game (or the same one again after Use the app's icon) is looked for afresh,
            // even if this start already looked for the old one.
            sceneLooked.remove(pkg)
        }
        // A new game's first images replace whatever was picked before.
        val replaced = if (changed) listOf(ArtKind.Cover, ArtKind.Hero) else fetched
        replaced.filterNot { it in fetched }.forEach { delete(File(dir, "$pkg.${it.suffix}")) }
        picks.edit { replaced.forEach { remove(pickKey(pkg, it)) } }
        save(pkg, ArtRecord.Matched(match))
        if (!hasScene(pkg)) findScene(pkg, match)
    }

    /**
     * For a matched [pkg]: a scene looked for when it has none, and its images fetched again when
     * they are gone (a restored setup) or didn't all come.
     */
    private fun finish(pkg: String) {
        val matched = records[pkg] as? ArtRecord.Matched ?: return
        if (!hasScene(pkg)) findScene(pkg, matched.match)
        if (pkg in unfinished || listOf(ArtKind.Cover, ArtKind.Hero).none { "$pkg.${it.suffix}" in present }) {
            refetch(pkg, matched.match)
        }
    }

    /**
     * Fetches [pkg]'s images again from what its record and picks name, after a restored setup
     * (Setup) or Android's backup brought the choices back without the images: a picked or
     * scored image from its URL, else the first one, which needs the key. A gallery pick came
     * back in the setup file.
     */
    private fun refetch(pkg: String, match: ArtMatch) {
        if (!refetched.add(pkg)) return
        scope.launch {
            writing.withLock {
                // Each slot on its own, so one that fails leaves the others to come, and whatever
                // came is drawn.
                var landed = false
                var failed = false
                suspend fun slot(to: File, key: String, auto: suspend () -> String?) {
                    if (to.name in present) return
                    val picked = picks.getString(key, null)?.takeIf { it.startsWith("http") }
                    try {
                        try {
                            download(picked ?: auto() ?: return, to)
                        } catch (e: HttpError) {
                            // A picked image SteamGridDB no longer has: the automatic one instead.
                            if (picked == null || e.code !in GONE) throw e
                            picks.edit { remove(key) }
                            download(auto() ?: return, to)
                        }
                        landed = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Offline: again when the network is back, or next start.
                        Log.w(TAG, "Fetching ${to.name} again failed: $e", e)
                        failed = true
                    }
                }
                try {
                    for (kind in listOf(ArtKind.Cover, ArtKind.Hero)) {
                        slot(File(dir, "$pkg.${kind.suffix}"), pickKey(pkg, kind)) { if (hasKey) best(kind, match.gameId) else null }
                    }
                    slot(sceneFile(pkg), pickKey(pkg, ArtKind.Scene)) { scenes.getString("$pkg.url", null) }
                } catch (e: CancellationException) {
                    refetched.remove(pkg)
                    throw e
                } finally {
                    if (landed) bump(pkg)
                }
                if (failed) {
                    refetched.remove(pkg)
                    failedOffline = true
                }
                unfinish(pkg, failed)
            }
        }
    }

    /** Art…'s A on the game in use while an image is missing: fetches what is missing, now. */
    fun retry(pkg: String) {
        val matched = records[pkg] as? ArtRecord.Matched ?: return
        refetched.remove(pkg)
        refetch(pkg, matched.match)
    }

    private fun unfinish(pkg: String, yes: Boolean) {
        val changed = if (yes) unfinished.add(pkg) else unfinished.remove(pkg)
        if (changed) pending.edit { putStringSet(PENDING, HashSet(unfinished)) }
    }

    fun useIcon(pkg: String) {
        ArtKind.entries.forEach { delete(File(dir, "$pkg.${it.suffix}")) }
        scenes.edit { remove(pkg); remove("$pkg.url") }
        picks.edit { ArtKind.entries.forEach { remove(pickKey(pkg, it)) }; remove(wideKey(pkg)) }
        unfinish(pkg, false)
        save(pkg, ArtRecord.Icon)
    }

    /**
     * Settings → Art → Clear all art: every file, match, scene and pick goes, so Home falls back
     * to icons and the hero band and art is looked up afresh, as on a first start.
     */
    suspend fun clear() = writing.withLock {
        withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { it.delete() }
            present.clear()
        }
        sp.edit { clear() }
        picks.edit { clear() }
        scenes.edit { clear() }
        val cleared = records.keys.toList()
        records.clear()
        looked.clear()
        sceneLooked.clear()
        unfinished.clear()
        pending.edit { clear() }
        cleared.forEach(::bump)
    }

    /** What the art takes on disk, in bytes. */
    suspend fun bytes(): Long = withContext(Dispatchers.IO) { dir.listFiles()?.sumOf { it.length() } ?: 0L }

    /**
     * What [pkg]'s matched game offers for a slot, only at sizes that are never upscaled where they
     * are shown: SteamGridDB's still covers or heroes in its own order (best voted first), or for
     * a scene the game's 16:9 Steam store screenshots at least 1920 wide, in the store's order.
     */
    suspend fun images(pkg: String, kind: ArtKind): List<ArtImage> {
        val match = (records[pkg] as? ArtRecord.Matched)?.match ?: return emptyList()
        val gameId = match.gameId
        val path = when (kind) {
            ArtKind.Cover -> "/grids/game/$gameId?dimensions=512x512,1024x1024"
            ArtKind.Hero -> "/heroes/game/$gameId?dimensions=1920x620,3840x1240"
            ArtKind.Scene -> {
                val appId = scenes.getString(pkg, null)?.toIntOrNull() ?: steamApp(match.name) ?: return emptyList()
                return steamShots(appId).map { ArtImage(it.getString("path_full"), it.getString("path_thumbnail"), 1920, 1080) }
            }
        }
        return api("$path&types=static&nsfw=false&humor=false").getJSONArray("data").objects().map {
            ArtImage(it.getString("url"), it.optString("thumb", it.getString("url")), it.getInt("width"), it.getInt("height"))
        }
    }

    /** Makes [image] [pkg]'s art for [kind] and remembers the pick; false if it couldn't be fetched. */
    suspend fun pick(pkg: String, kind: ArtKind, image: ArtImage): Boolean = writing.withLock {
        try {
            download(image.url, File(dir, "$pkg.${kind.suffix}"))
            picks.edit { putString(pickKey(pkg, kind), image.url) }
            bump(pkg)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Fetching ${kind.suffix} for $pkg failed", e)
            false
        }
    }

    /** The shot the score chose for [pkg]'s scene, when it was fetched automatically. */
    fun autoScene(pkg: String): String? {
        revision(pkg)
        return scenes.getString("$pkg.url", null)
    }

    /**
     * Makes an image from the gallery [pkg]'s [kind] art: the largest centered square for a cover,
     * the largest centered 16:9 for a scene, scaled down to the slot's size and never up. Returns
     * why not when the crop is smaller than the slot is shown (hard rule 4) or it can't be read;
     * null when done. An app without a game keeps the images as its own.
     */
    suspend fun useImage(pkg: String, kind: ArtKind, uri: Uri): String? = writing.withLock {
        val (outW, outH) = if (kind == ArtKind.Cover) COVER_PX to COVER_PX else SCENE_W to SCENE_H
        val minW = if (kind == ArtKind.Cover) COVER_MIN_PX else SCENE_W
        try {
            val bitmap = withContext(Dispatchers.IO) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    // The crop in the source's pixels, then the scale that brings it to the slot.
                    val cropW = minOf(w, h * outW / outH)
                    val cropH = cropW * outH / outW
                    if (cropW < minW) throw TooSmall(cropW, cropH)
                    val scale = minOf(1f, outW.toFloat() / cropW)
                    val tw = (w * scale).roundToInt()
                    val th = (h * scale).roundToInt()
                    val cw = (cropW * scale).roundToInt().coerceAtMost(tw)
                    val ch = (cropH * scale).roundToInt().coerceAtMost(th)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSize(tw, th)
                    decoder.crop = Rect((tw - cw) / 2, (th - ch) / 2, (tw - cw) / 2 + cw, (th - ch) / 2 + ch)
                }
            }
            withContext(Dispatchers.IO) {
                val to = File(dir, "$pkg.${kind.suffix}")
                val part = File(to.path + ".part")
                part.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                bitmap.recycle()
                if (!part.renameTo(to)) throw IOException("Couldn't save $to")
                present.add(to.name)
            }
            picks.edit { putString(pickKey(pkg, kind), uri.toString()) }
            if (kind == ArtKind.Scene) scenes.edit { putString(pkg, scenes.getString(pkg, null) ?: NO_SCENE) }
            if (records[pkg] !is ArtRecord.Matched) save(pkg, ArtRecord.Own) else bump(pkg)
            null
        } catch (e: TooSmall) {
            "Too small: ${e.w}×${e.h}, needs ${if (kind == ArtKind.Cover) "$COVER_MIN_PX×$COVER_MIN_PX" else "${SCENE_W}×$SCENE_H"}"
        } catch (e: Exception) {
            Log.w(TAG, "Gallery image for $pkg failed", e)
            "Couldn't read that image"
        }
    }

    private class TooSmall(val w: Int, val h: Int) : RuntimeException()

    /**
     * What fills Home behind [pkg]: its scene or its hero, never both (owner, F24). A scene only when
     * the owner chose it in Art…, or when there is no hero (a game SteamGridDB has none for, a scene
     * from the gallery); otherwise SteamGridDB's first hero (owner, 0.3.0).
     */
    fun wide(pkg: String): ArtKind {
        revision(pkg)
        val hasScene = file(pkg, ArtKind.Scene) != null
        val sceneChosen = picks.getString(wideKey(pkg), null) == ArtKind.Scene.suffix
        val hasHero = file(pkg, ArtKind.Hero) != null
        return if (hasScene && (sceneChosen || !hasHero)) ArtKind.Scene else ArtKind.Hero
    }

    /** Makes [kind] (the hero or the scene) what fills Home behind [pkg]. */
    fun useWide(pkg: String, kind: ArtKind) {
        picks.edit { putString(wideKey(pkg), kind.suffix) }
        bump(pkg)
    }

    /**
     * The url of the image picked for [pkg]'s [kind] slot; null when it is the automatic one. Reads
     * the revision first, so a composable showing the pick redraws when it changes.
     */
    fun picked(pkg: String, kind: ArtKind): String? {
        revision(pkg)
        return picks.getString(pickKey(pkg, kind), null)
    }

    /** A picker thumbnail, at most [maxPx] on its longer side; kept in memory while the launcher runs. */
    suspend fun thumbnail(url: String, maxPx: Int): ImageBitmap? {
        thumbs.get(url)?.let { return it }
        return try {
            withContext(Dispatchers.IO) {
                val bytes = connect(url).useConnection { it.inputStream.readBytes() }
                decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), maxPx)
            }?.also { thumbs.put(url, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail $url failed: $e")
            null
        }
    }

    private fun pickKey(pkg: String, kind: ArtKind) = "$pkg.${kind.suffix}"
    private fun wideKey(pkg: String) = "$pkg.wide"

    private fun sceneFile(pkg: String) = File(dir, "$pkg.${ArtKind.Scene.suffix}")

    // A scene copied in by hand has no entry but counts (once the launcher has started again).
    private fun hasScene(pkg: String) = scenes.contains(pkg) || sceneFile(pkg).name in present

    /**
     * In the background, once per start: the game on Steam by its SteamGridDB name, then the best of
     * its 16:9 store screenshots at least 1920 wide ([sceneScore]). Not on Steam, or no shot big
     * enough, is remembered; a network failure tries again next start.
     */
    private fun findScene(pkg: String, match: ArtMatch) {
        if (!sceneLooked.add(pkg)) return
        scope.launch {
            writing.withLock {
                try {
                    val appId = steamApp(match.name)
                    val shot = appId?.let { bestShot(it) }
                    if (shot != null) download(shot, sceneFile(pkg))
                    // The app id is kept even without a shot, so the picker can still list them.
                    scenes.edit {
                        putString(pkg, appId?.toString() ?: NO_SCENE)
                        if (shot != null) putString("$pkg.url", shot)
                    }
                    if (shot != null) bump(pkg)
                } catch (e: CancellationException) {
                    sceneLooked.remove(pkg)
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Scene for $pkg failed: $e", e)
                    sceneLooked.remove(pkg)
                    failedOffline = true
                }
            }
        }
    }

    /** Steam's store search: the first app named the same, else the first close match. */
    private suspend fun steamApp(name: String): Int? {
        val items = json("$STORE/api/storesearch/?term=${Uri.encode(name.trim())}&cc=us&l=english")
            .optJSONArray("items")?.objects().orEmpty().filter { it.optString("type") == "app" }
        val pick = items.firstOrNull { words(it.getString("name")) == words(name) }
            ?: items.firstOrNull { closeMatch(name, it.getString("name")) }
        return pick?.getInt("id")
    }

    /** The full-size URL of [appId]'s best screenshot, judged from the small copies. */
    private suspend fun bestShot(appId: Int): String? =
        steamShots(appId).maxByOrNull { sceneScore(it.getString("path_thumbnail")) }?.getString("path_full")

    /** [appId]'s store screenshots that are 16:9 and at least 1920 wide. */
    private suspend fun steamShots(appId: Int) = json("$STORE/api/appdetails?appids=$appId&filters=screenshots")
        .optJSONObject("$appId")?.optJSONObject("data")?.optJSONArray("screenshots")?.objects().orEmpty()
        .filter { fullHd(it.getString("path_full")) }

    /**
     * Rich color and mid-dark, so text reads on it: the mean saturation less twice how far the mean
     * luma is from [SCENE_LUMA] (both 0–255). Tuned on the owner's two hand picks, NTE's and
     * Stardew's, which it chooses out of 12 and 16; the darkest shots lose to them.
     */
    private suspend fun sceneScore(url: String): Float = withContext(Dispatchers.IO) {
        val bytes = connect(url).useConnection { it.inputStream.readBytes() }
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(SAMPLE_W, SAMPLE_H)
        }
        val pixels = IntArray(SAMPLE_W * SAMPLE_H)
        bitmap.getPixels(pixels, 0, SAMPLE_W, 0, 0, SAMPLE_W, SAMPLE_H)
        bitmap.recycle()
        var luma = 0f
        var saturation = 0f
        for (c in pixels) {
            val r = c shr 16 and 0xFF
            val g = c shr 8 and 0xFF
            val b = c and 0xFF
            val max = maxOf(r, g, b)
            luma += 0.299f * r + 0.587f * g + 0.114f * b
            if (max > 0) saturation += (max - minOf(r, g, b)) * 255f / max
        }
        saturation / pixels.size - 2 * abs(luma / pixels.size - SCENE_LUMA)
    }

    private fun save(pkg: String, record: ArtRecord) {
        records[pkg] = record
        bump(pkg)
        sp.edit { putString(pkg, encode(record)) }
    }

    private fun delete(file: File) {
        file.delete()
        present.remove(file.name)
    }

    /** A game's first cover or hero, as a match first fetches it. */
    private suspend fun best(kind: ArtKind, gameId: Int) = when (kind) {
        ArtKind.Cover -> best("/grids/game/$gameId?dimensions=512x512,1024x1024", preferWidth = 512)
        else -> best("/heroes/game/$gameId?dimensions=1920x620,3840x1240", preferWidth = 1920)
    }

    /** The best-scored still image's URL at the preferred width, else the best at any listed size. */
    private suspend fun best(path: String, preferWidth: Int): String? {
        val images = api("$path&types=static&nsfw=false&humor=false").getJSONArray("data").objects()
        val pick = images.firstOrNull { it.getInt("width") == preferWidth } ?: images.firstOrNull()
        return pick?.getString("url")
    }

    // Written beside the old file and renamed over it, so a failed download keeps the old art.
    private suspend fun download(url: String?, to: File) = withContext(Dispatchers.IO) {
        if (url == null) {
            delete(to)
            return@withContext
        }
        val part = File(to.path + ".part")
        connect(url).useConnection { part.outputStream().use { out -> it.inputStream.copyTo(out) } }
        if (!part.renameTo(to)) throw IOException("Couldn't save $to")
        present.add(to.name)
    }

    // The key goes to SteamGridDB only, never to Steam.
    private suspend fun api(path: String) = json(BASE + path) { setRequestProperty("Authorization", "Bearer $key") }

    private suspend fun json(url: String, setup: HttpURLConnection.() -> Unit = {}): JSONObject = withContext(Dispatchers.IO) {
        connect(url).apply(setup).useConnection { JSONObject(it.inputStream.bufferedReader().readText()) }
    }

    private fun connect(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
    }

    private inline fun <T> HttpURLConnection.useConnection(read: (HttpURLConnection) -> T): T = try {
        if (responseCode != HttpURLConnection.HTTP_OK) throw HttpError(responseCode)
        read(this)
    } finally {
        disconnect()
    }

    /** An answer other than 200, by its code (a picked image's 404 drops the pick). */
    private class HttpError(val code: Int) : IOException("HTTP $code")

    private companion object {
        const val PENDING = "unfinished"
        /** Gone for good: a picked URL answering these falls back to the automatic image. */
        val GONE = setOf(HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE)
        const val TAG = "Art"
        const val BASE = "https://www.steamgriddb.com/api/v2"
        const val TIMEOUT_MS = 15_000
        const val STORE = "https://store.steampowered.com"
        const val NO_SCENE = "-"
        const val SAMPLE_W = 64
        const val SAMPLE_H = 36
        const val SCENE_LUMA = 73f
        // Thumbnails held at once; a game rarely has more covers or heroes than this.
        const val THUMB_CACHE = 60
        // Decoded art held, in bytes: a carousel cover (365 px square) is about 0.5 MB, so about
        // 45 of them; a hero at the screen's width about 5 MB, a scene 8 MB.
        const val COVER_CACHE = 24 shl 20
        const val WIDE_CACHE = 24 shl 20
        // How long a returning network has to hold before what failed runs again.
        const val NETWORK_SETTLE_MS = 3_000L

        // A gallery image is saved at the size SteamGridDB's covers and Steam's shots come in. A
        // cover may be smaller, down to the focused tile (192 dp, 346 px on the Portal).
        const val COVER_PX = 512
        const val COVER_MIN_PX = 346
        const val SCENE_W = 1920
        const val SCENE_H = 1080
        const val JPEG_QUALITY = 92

        /** Steam names the size in the file ("ss_….1920x1080.jpg"): 16:9 and at least 1920 wide. */
        fun fullHd(url: String): Boolean {
            val size = Regex("""\.(\d+)x(\d+)\.""").find(url)?.destructured ?: return false
            val (w, h) = size.let { (w, h) -> w.toInt() to h.toInt() }
            return w >= 1920 && w * 9 == h * 16
        }

        fun encode(record: ArtRecord) = when (record) {
            is ArtRecord.Matched -> listOf("m", record.match.gameId, record.match.name, record.match.year ?: "").joinToString("\t")
            ArtRecord.Icon -> "i"
            ArtRecord.NoMatch -> "n"
            ArtRecord.Own -> "o"
        }

        fun decode(value: String?): ArtRecord? {
            val parts = value?.split('\t') ?: return null
            return when (parts[0]) {
                "m" -> parts.getOrNull(1)?.toIntOrNull()?.let {
                    ArtRecord.Matched(ArtMatch(it, parts.getOrElse(2) { "" }, parts.getOrNull(3)?.toIntOrNull()))
                }
                "i" -> ArtRecord.Icon
                "n" -> ArtRecord.NoMatch
                "o" -> ArtRecord.Own
                else -> null
            }
        }

        fun year(epochSeconds: Long) =
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = epochSeconds * 1000 }.get(Calendar.YEAR)

        fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

        /**
         * Close enough to take without asking: the same words once case, symbols and punctuation
         * are gone, or the label is the game's name before a subtitle ("NTE" for "NTE: Neverness
         * to Everness").
         */
        fun closeMatch(label: String, name: String): Boolean {
            val l = words(label)
            return l.isNotEmpty() && (l == words(name) || l == words(name.substringBefore(':')) || l == words(name.substringBefore(" - ")))
        }

        fun words(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }
}

/**
 * Android's, Google's and LineageOS's own apps: never games, but their one-word names are
 * ("Chrome" and "Calculator: The Game" both matched), so a favorite from these isn't looked up
 * without asking.
 */
fun isPlatformApp(pkg: String) = PLATFORM_PREFIXES.any { pkg.startsWith(it) }

private val PLATFORM_PREFIXES = listOf("com.android.", "com.google.", "org.lineageos.")

/**
 * An app's art decoded at no more than [maxPx] on its longer side (only ever scaled down), or
 * null while it loads or when there is none. Decoded again when the art changes. Art already in
 * memory comes back on this very frame, for a new tile and a moved focus alike; otherwise the
 * last image stays until the next is decoded, so a crossfade has no gap.
 */
@Composable
fun rememberArt(art: Art?, pkg: String, kind: ArtKind, maxPx: Int): ImageBitmap? {
    val revision = art?.revision(pkg) ?: 0
    val file = art?.file(pkg, kind)
    val hit = remember(art, file, revision, maxPx) { if (art != null && file != null) art.cached(file, kind, maxPx, revision) else null }
    val bitmap by produceState(hit, art, file, revision, maxPx) {
        value = hit ?: if (art != null && file != null) art.bitmap(pkg, file, kind, maxPx, revision) else null
    }
    return if (file == null) null else hit ?: bitmap
}

// Covers decode two at a time, so a new row's dozen doesn't take every core from its slide.
private val COVER_LANE = Dispatchers.IO.limitedParallelism(2)
// One full-width decode at a time: requests queue behind it, and a passed one drops out unstarted.
private val WIDE_LANE = Dispatchers.IO.limitedParallelism(1)

private fun bitmaps(bytes: Int) = object : LruCache<String, ImageBitmap>(bytes) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().allocationByteCount
}

/**
 * A signal each time the default network is usable again (validated) after being lost or not
 * yet usable; the first report, at registering, counts, so a launcher started offline sees the
 * network arrive. From the application's context, as the callback outlives its removal.
 */
private fun networkReturns(context: Context) = callbackFlow {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val callback = object : ConnectivityManager.NetworkCallback() {
        private var usable = false

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val now = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (now && !usable) trySend(Unit)
            usable = now
        }

        override fun onLost(network: Network) {
            usable = false
        }
    }
    connectivity.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
    awaitClose { connectivity.unregisterNetworkCallback(callback) }
}

/** An image file decoded off the main thread, its longer side at most [maxPx]; null until then. */
@Composable
fun rememberImage(file: File?, maxPx: Int): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, file, maxPx) {
        value = file?.let { withContext(Dispatchers.IO) { decode(it, maxPx) } }
    }
    return bitmap
}

private fun decode(file: File, maxPx: Int): ImageBitmap? = decode(ImageDecoder.createSource(file), maxPx)

private fun decode(source: ImageDecoder.Source, maxPx: Int): ImageBitmap? = try {
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val longer = maxOf(info.size.width, info.size.height)
        if (longer > maxPx) {
            decoder.setTargetSize(info.size.width * maxPx / longer, info.size.height * maxPx / longer)
        }
    }.asImageBitmap()
} catch (e: IOException) {
    null
}
