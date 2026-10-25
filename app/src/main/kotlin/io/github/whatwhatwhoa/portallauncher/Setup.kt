package io.github.whatwhatwhoa.portallauncher

import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.core.content.edit
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * The owner's setup as one file in Download (F26), for what wipes the app's data: an uninstall,
 * or a change of signing key. A normal update keeps the data and needs none of this. The file
 * holds favorites, dock, categories, hidden apps, every setting and the art choices. The
 * SteamGridDB key stays out, so the file can be passed around. Downloaded art stays out too and
 * is fetched again ([Art.lookUp]); gallery art and wallpaper files go in, since nothing could
 * fetch them.
 */
class Setup(private val context: Context) {
    /** Writes the file, over this install's earlier one, so there is one file and not a pile. */
    fun save() {
        val resolver = context.contentResolver
        val downloads = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // Without a storage permission the query sees only files this install made.
        val earlier = resolver.query(
            downloads, arrayOf(MediaStore.Downloads._ID), "${MediaStore.Downloads.DISPLAY_NAME}=?", arrayOf(FILE_NAME), null,
        )?.use { if (it.moveToFirst()) ContentUris.withAppendedId(downloads, it.getLong(0)) else null }
        val uri = earlier ?: resolver.insert(downloads, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, MIME)
        }) ?: throw IOException("Download refused the file")
        val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("Couldn't open $uri")
        JsonWriter(out.bufferedWriter()).use { w ->
            w.setIndent("  ")
            w.beginObject()
            w.name("format").value(FORMAT)
            w.name("version").value(BuildConfig.VERSION_NAME)
            w.name("saved").value(Instant.now().toString())
            w.name("prefs").beginObject()
            for (name in PREFS) {
                w.name(name).beginObject()
                prefs(name).all.toSortedMap().forEach { (key, value) ->
                    if (name == LAUNCHER && key in PRIVATE) return@forEach
                    w.name(key)
                    writeValue(w, value)
                }
                w.endObject()
            }
            w.endObject()
            w.name("files").beginObject()
            for ((name, file) in ownFiles()) w.name(name).value(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            w.endObject()
            w.endObject()
        }
    }

    /**
     * Replaces the setup with the one in [uri]; null when done, else why not. Everything is read
     * before anything changes, so a wrong or broken file leaves the setup as it was. The caller
     * then [restart]s, since every part of the launcher read its setup when it started.
     */
    fun restore(uri: Uri): String? {
        val staging = File(context.cacheDir, "setup").apply { deleteRecursively(); mkdirs() }
        var format = 0
        val maps = HashMap<String, Map<String, Any>>()
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return "Couldn't read that file"
            JsonReader(input.bufferedReader()).use { r ->
                r.beginObject()
                while (r.hasNext()) when (r.nextName()) {
                    "format" -> format = r.nextInt()
                    "prefs" -> {
                        r.beginObject()
                        while (r.hasNext()) {
                            val name = r.nextName()
                            if (name in PREFS) maps[name] = readMap(r) else r.skipValue()
                        }
                        r.endObject()
                    }
                    "files" -> {
                        r.beginObject()
                        while (r.hasNext()) {
                            val name = r.nextName()
                            if (!FILE_PATH.matches(name)) {
                                r.skipValue()
                                continue
                            }
                            val file = File(staging, name).apply { parentFile?.mkdirs() }
                            file.writeBytes(Base64.decode(r.nextString(), Base64.NO_WRAP))
                        }
                        r.endObject()
                    }
                    else -> r.skipValue()
                }
                r.endObject()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reading $uri failed", e)
            staging.deleteRecursively()
            return if (format == 0) "Not a Portal setup" else "Couldn't read that file"
        }
        if (format !in 1..FORMAT || LAUNCHER !in maps) {
            staging.deleteRecursively()
            return if (format > FORMAT) "Saved by a newer Portal" else "Not a Portal setup"
        }

        for (name in PREFS) {
            val sp = prefs(name)
            val kept = if (name == LAUNCHER) sp.all.filterKeys { it in PRIVATE } else emptyMap()
            // Committed, not applied: the process ends right after.
            sp.edit(commit = true) {
                clear()
                (maps[name].orEmpty().filterKeys { name != LAUNCHER || it !in PRIVATE } + kept).forEach { (key, value) ->
                    value?.let { putValue(key, it) }
                }
            }
        }
        // Downloaded art is fetched again for the restored choices; the file brings the rest.
        File(context.filesDir, "art").listFiles()?.forEach { it.delete() }
        staging.walkTopDown().filter { it.isFile }.forEach { file ->
            val to = File(context.filesDir, file.relativeTo(staging).path).apply { parentFile?.mkdirs() }
            file.copyTo(to, overwrite = true)
        }
        staging.deleteRecursively()
        return null
    }

    /** Starts the launcher afresh, so it reads the restored setup. */
    fun restart() {
        context.startActivity(Intent.makeRestartActivityTask(ComponentName(context, MainActivity::class.java)))
        Runtime.getRuntime().exit(0)
    }

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** The files only the owner had: art picked from the gallery and the wallpapers. */
    private fun ownFiles(): List<Pair<String, File>> {
        val art = File(context.filesDir, "art")
        // A gallery pick is stored as its content: URI under the art file's own name.
        val gallery = prefs("picks").all.filterValues { (it as? String)?.startsWith("content:") == true }.keys
            .map { "art/$it" to File(art, it) }
        val wallpapers = File(context.filesDir, "backdrops").listFiles().orEmpty().map { "backdrops/${it.name}" to it }
        return (gallery + wallpapers).filter { (_, file) -> file.isFile }
    }

    private fun writeValue(w: JsonWriter, value: Any?) {
        when (value) {
            is String -> w.value(value)
            is Boolean -> w.value(value)
            // JSON has one kind of number; the type rides along so it reads back as it was.
            is Int -> w.beginObject().name("int").value(value.toLong()).endObject()
            is Long -> w.beginObject().name("long").value(value).endObject()
            is Float -> w.beginObject().name("float").value(value.toDouble()).endObject()
            is Set<*> -> {
                w.beginArray()
                value.sortedBy { it.toString() }.forEach { w.value(it.toString()) }
                w.endArray()
            }
            else -> w.nullValue()
        }
    }

    private fun readMap(r: JsonReader): Map<String, Any> {
        val map = HashMap<String, Any>()
        r.beginObject()
        while (r.hasNext()) {
            val key = r.nextName()
            when (r.peek()) {
                JsonToken.STRING -> map[key] = r.nextString()
                JsonToken.BOOLEAN -> map[key] = r.nextBoolean()
                JsonToken.BEGIN_ARRAY -> {
                    val set = HashSet<String>()
                    r.beginArray()
                    while (r.hasNext()) set += r.nextString()
                    r.endArray()
                    map[key] = set
                }
                JsonToken.BEGIN_OBJECT -> {
                    r.beginObject()
                    when (r.nextName()) {
                        "int" -> map[key] = r.nextInt()
                        "long" -> map[key] = r.nextLong()
                        "float" -> map[key] = r.nextDouble().toFloat()
                        else -> r.skipValue()
                    }
                    r.endObject()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return map
    }

    @Suppress("UNCHECKED_CAST")
    private fun SharedPreferences.Editor.putValue(key: String, value: Any) {
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> putStringSet(key, value as Set<String>)
        }
    }

    companion object {
        const val TAG = "Setup"
        const val FILE_NAME = "Portal setup.json"
        private const val MIME = "application/json"
        /** Raised when the file changes in a way an older launcher would misread. */
        private const val FORMAT = 1
        private const val LAUNCHER = "launcher"
        /** Prefs (Prefs, Art) that make up the setup; Art's "art" holds the game each app matched. */
        private val PREFS = listOf(LAUNCHER, "art", "picks", "scenes")
        /** The SteamGridDB key is the owner's own; a restore keeps whatever key is set now. */
        private val PRIVATE = setOf("art.key")
        private val FILE_PATH = Regex("""(art|backdrops)/[^/\\.][^/\\]*""")
        /** What the picker offers: the file as saved, or as another app may have typed it. */
        val PICKER_TYPES = arrayOf(MIME, "application/octet-stream", "text/plain")
    }
}
