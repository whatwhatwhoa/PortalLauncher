package io.github.whatwhatwhoa.portallauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * Art from SteamGridDB: a square cover and a 1920 px wide hero per app, downloaded once into the
 * app's files and never upscaled (only sizes at least as big as they are shown are asked for).
 * Favorites and apps Android marks as games are looked up by their label and taken only on a
 * close match; anything else waits for the owner (+ → Art…). A matched game also gets a scene
 * from Steam when it is sold there; anything else keeps the hero band. Lookups need the owner's
 * own SteamGridDB [key] (Launcher Settings → Art); without one, only gallery images work.
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

    /** Package → record; also what composables watch, so a new match redraws its art. */
    private val records = mutableStateMapOf<String, ArtRecord>().apply {
        sp.all.forEach { (pkg, value) -> decode(value as? String)?.let { put(pkg, it) } }
    }

    // Bumped on every change, so a cover replaced under the same file name is decoded again.
    private val revisions = mutableStateMapOf<String, Int>()

    fun record(pkg: String): ArtRecord? = records[pkg]
    fun revision(pkg: String) = revisions[pkg] ?: 0

    fun file(pkg: String, kind: ArtKind): File? = File(dir, "$pkg.${kind.suffix}").takeIf {
        (records[pkg] is ArtRecord.Matched || records[pkg] == ArtRecord.Own) && it.exists()
    }

    /**
     * Looks up every app here that has no record yet, one at a time, once per launcher start; a
     * matched app without a scene has one looked for too.
     */
    fun lookUp(apps: List<AppEntry>) {
        val unique = apps.distinctBy { it.component.packageName }
        unique.forEach { app ->
            val pkg = app.component.packageName
            val matched = records[pkg] as? ArtRecord.Matched
            if (matched != null && !hasScene(pkg)) findScene(pkg, matched.match)
        }
        if (!hasKey) return
        val todo = unique.filter { it.component.packageName !in records && looked.add(it.component.packageName) }
        if (todo.isEmpty()) return
        scope.launch {
            for (app in todo) {
                val pkg = app.component.packageName
                val match = try {
                    search(app.label).firstOrNull { closeMatch(app.label, it.name) }
                } catch (e: Exception) {
                    // Offline or the service is down: try again next start.
                    Log.w(TAG, "Art lookup for ${app.label} failed: $e", e)
                    looked.remove(pkg)
                    continue
                }
                if (pkg in records) continue
                if (match == null || !use(pkg, match)) save(pkg, ArtRecord.NoMatch)
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
     * Downloads [match]'s cover and hero and makes it [pkg]'s art, then looks for its scene; false
     * if nothing could be fetched. Another game's scene goes; the same game's stays.
     */
    suspend fun use(pkg: String, match: ArtMatch): Boolean = writing.withLock {
        try {
            val before = (records[pkg] as? ArtRecord.Matched)?.match?.gameId
            val cover = best("/grids/game/${match.gameId}?dimensions=512x512,1024x1024", preferWidth = 512)
            val hero = best("/heroes/game/${match.gameId}?dimensions=1920x620,3840x1240", preferWidth = 1920)
            if (cover == null && hero == null) return false
            download(cover, File(dir, "$pkg.${ArtKind.Cover.suffix}"))
            download(hero, File(dir, "$pkg.${ArtKind.Hero.suffix}"))
            if (before != match.gameId) {
                sceneFile(pkg).delete()
                scenes.edit { remove(pkg); remove("$pkg.url") }
                picks.edit { remove(pickKey(pkg, ArtKind.Scene)) }
                // A new game (or the same one again after Use the app's icon) is looked for afresh,
                // even if this start already looked for the old one.
                sceneLooked.remove(pkg)
            }
            // A new game's first images replace whatever was picked before.
            picks.edit { remove(pickKey(pkg, ArtKind.Cover)); remove(pickKey(pkg, ArtKind.Hero)) }
            save(pkg, ArtRecord.Matched(match))
            if (!hasScene(pkg)) findScene(pkg, match)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Fetching art for $pkg failed", e)
            false
        }
    }

    fun useIcon(pkg: String) {
        ArtKind.entries.forEach { File(dir, "$pkg.${it.suffix}").delete() }
        scenes.edit { remove(pkg); remove("$pkg.url") }
        picks.edit { ArtKind.entries.forEach { remove(pickKey(pkg, it)) } }
        save(pkg, ArtRecord.Icon)
    }

    /**
     * Settings → Art → Clear all art: every file, match, scene and pick goes, so Home falls back
     * to icons and the hero band and art is looked up afresh, as on a first start.
     */
    suspend fun clear() = writing.withLock {
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
        sp.edit { clear() }
        picks.edit { clear() }
        scenes.edit { clear() }
        val cleared = records.keys.toList()
        records.clear()
        looked.clear()
        sceneLooked.clear()
        cleared.forEach { revisions[it] = revision(it) + 1 }
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
            revisions[pkg] = revision(pkg) + 1
            true
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
            }
            picks.edit { putString(pickKey(pkg, kind), uri.toString()) }
            if (kind == ArtKind.Scene) scenes.edit { putString(pkg, scenes.getString(pkg, null) ?: NO_SCENE) }
            if (records[pkg] !is ArtRecord.Matched) save(pkg, ArtRecord.Own) else revisions[pkg] = revision(pkg) + 1
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

    private fun sceneFile(pkg: String) = File(dir, "$pkg.${ArtKind.Scene.suffix}")

    // A scene copied in by hand has no entry but counts.
    private fun hasScene(pkg: String) = scenes.contains(pkg) || sceneFile(pkg).exists()

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
                    if (shot != null) revisions[pkg] = revision(pkg) + 1
                } catch (e: Exception) {
                    Log.w(TAG, "Scene for $pkg failed: $e", e)
                    sceneLooked.remove(pkg)
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
        revisions[pkg] = revision(pkg) + 1
        sp.edit { putString(pkg, encode(record)) }
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
            to.delete()
            return@withContext
        }
        val part = File(to.path + ".part")
        connect(url).useConnection { part.outputStream().use { out -> it.inputStream.copyTo(out) } }
        if (!part.renameTo(to)) throw IOException("Couldn't save $to")
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
        if (responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP $responseCode")
        read(this)
    } finally {
        disconnect()
    }

    private companion object {
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
 * An app's art decoded at no more than [maxPx] on its longer side (only ever scaled down), or
 * null while it loads or when there is none. Decoded again when the art changes.
 */
@Composable
fun rememberArt(art: Art?, pkg: String, kind: ArtKind, maxPx: Int): ImageBitmap? {
    val revision = art?.revision(pkg) ?: 0
    val file = art?.file(pkg, kind)
    val bitmap by produceState<ImageBitmap?>(null, file, revision, maxPx) {
        value = file?.let { withContext(Dispatchers.IO) { decode(it, maxPx) } }
    }
    return bitmap
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
