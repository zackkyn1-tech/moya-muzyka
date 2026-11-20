package ua.guitar.archive

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class Imported(val file: String, val name: String, val mime: String)

/** Зберігає бібліотеку в JSON, а медіафайли — у внутрішній папці додатку. */
class Store(private val ctx: Context) {
    val mediaDir = File(ctx.filesDir, "media").apply { mkdirs() }
    private val dataFile = File(ctx.filesDir, "library.json")
    private val _lib = MutableStateFlow(load())
    val library: StateFlow<Library> = _lib.asStateFlow()
    val current: Library get() = _lib.value

    fun file(name: String) = File(mediaDir, name)

    private fun load(): Library {
        if (!dataFile.exists()) return Library()
        return try {
            LibraryJson.decode(dataFile.readText())
        } catch (e: Exception) {
            dataFile.copyTo(File(ctx.filesDir, "library-broken-${System.currentTimeMillis()}.json"), true)
            Library()
        }
    }

    fun update(f: (Library) -> Library) = synchronized(this) {
        val n = f(_lib.value)
        _lib.value = n
        val tmp = File(ctx.filesDir, "library.json.tmp")
        tmp.writeText(LibraryJson.encode(n))
        if (!tmp.renameTo(dataFile)) {
            dataFile.writeText(tmp.readText()); tmp.delete()
        }
    }

    fun updateSong(id: String, f: (Song) -> Song) =
        update { l -> l.copy(songs = l.songs.map { if (it.id == id) f(it) else it }) }

    fun updateCategory(id: String, f: (Category) -> Category) =
        update { l -> l.copy(categories = l.categories.map { if (it.id == id) f(it) else it }) }

    fun newMediaFile(ext: String) = File(mediaDir, "${newId()}.$ext")

    fun deleteFiles(names: Collection<String?>) {
        names.filterNotNull().forEach { file(it).delete() }
    }

    fun deleteSong(id: String) {
        val s = current.songs.find { it.id == id } ?: return
        update { it.copy(songs = it.songs.filter { x -> x.id != id }) }
        deleteFiles(s.allFiles())
    }

    fun deleteCategory(id: String) {
        val c = current.categories.find { it.id == id } ?: return
        val songs = current.songs.filter { it.categoryId == id }
        update { l ->
            l.copy(
                categories = l.categories.filter { it.id != id },
                songs = l.songs.filter { it.categoryId != id },
            )
        }
        deleteFiles(listOf(c.cover) + songs.flatMap { it.allFiles() })
    }

    /** Копіює вибраний користувачем файл у сховище додатку. */
    fun importUri(uri: Uri): Imported {
        val cr = ctx.contentResolver
        var name = "Файл"
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        } catch (_: Exception) {
        }
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..6 }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val f = newMediaFile(ext)
        (cr.openInputStream(uri) ?: error("Не вдалося відкрити файл")).use { i ->
            f.outputStream().use { i.copyTo(it) }
        }
        return Imported(f.name, name, mime)
    }

    fun lastModified(): Long = dataFile.lastModified()

    fun exportTo(uri: Uri) {
        val os = ctx.contentResolver.openOutputStream(uri) ?: error("Не вдалося створити файл")
        os.use { writeBackup(it) }
    }

    /**
     * Резервна копія — звичайний .zip, який можна відкрити й без додатку:
     * Пісні/<Категорія>/<Пісня>/ з текст.txt, примітки.txt, папкою Голосові …
     * library.json + files.json потрібні, щоб відновити все назад у додаток.
     */
    fun writeBackup(out: OutputStream) {
        val lib = current
        val usedPaths = HashSet<String>()
        fun unique(base: String, ext: String): String {
            val suffix = if (ext.isEmpty()) "" else ".$ext"
            var p = base + suffix
            var i = 2
            while (!usedPaths.add(p.lowercase())) p = "$base ($i)$suffix".also { i++ }
            return p
        }
        val media = LinkedHashMap<String, String>() // ім'я файлу в додатку -> шлях у zip
        val texts = LinkedHashMap<String, String>()
        fun addMedia(name: String?, base: String) {
            if (name == null || name in media || !file(name).exists()) return
            media[name] = unique(base, name.substringAfterLast('.', ""))
        }
        fun addText(base: String, content: String) {
            if (content.isNotBlank()) texts[unique(base, "txt")] = content.trim() + "\n"
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US)
        for (c in lib.categories) {
            val cd = unique("Пісні/${safeName(c.name)}", "")
            addMedia(c.cover, "$cd/обкладинка")
            for (s in lib.songs.filter { it.categoryId == c.id }.sortedBy { it.title.lowercase() }) {
                val title = if (s.artist.isNotBlank()) "${s.artist} - ${s.title}" else s.title
                val sd = unique("$cd/${safeName(title)}", "")
                addText("$sd/інфо", buildString {
                    appendLine(s.title)
                    if (s.artist.isNotBlank()) appendLine("Виконавець: ${s.artist}")
                    appendLine("Категорія: ${c.name}")
                    appendLine("Додано: ${fmtDate(s.createdAt)}")
                    if (s.recordings.isNotEmpty()) appendLine("Голосових: ${s.recordings.size}")
                })
                addText("$sd/примітки", s.notes.sortedBy { it.createdAt }
                    .joinToString("\n\n") { "— ${fmtDate(it.createdAt)} —\n${it.text.trim()}" })
                addText("$sd/текст", s.lyrics)
                addText("$sd/акорди", s.chords)
                addMedia(s.cover, "$sd/обкладинка")
                s.recordings.sortedBy { it.createdAt }.forEach {
                    addMedia(it.file, "$sd/Голосові/${stamp.format(Date(it.createdAt))} ${safeName(it.name)}")
                }
                s.audio.forEach { addMedia(it.file, "$sd/Музика/${safeName(it.name.substringBeforeLast('.'))}") }
                s.noteImages.forEachIndexed { i, n -> addMedia(n, "$sd/Ноти/фото ${i + 1}") }
                s.noteFiles.forEach { addMedia(it.file, "$sd/Ноти/${safeName(it.name.substringBeforeLast('.'))}") }
            }
        }
        ZipOutputStream(BufferedOutputStream(out)).use { z ->
            fun put(path: String, bytes: ByteArray) {
                z.putNextEntry(ZipEntry(path)); z.write(bytes); z.closeEntry()
            }
            put("ПРОЧИТАЙ.txt", BACKUP_README.toByteArray())
            put("library.json", LibraryJson.encode(lib).toByteArray())
            put("files.json", JSONObject(media as Map<*, *>).toString(1).toByteArray())
            texts.forEach { (p, t) -> put(p, t.toByteArray()) }
            z.setLevel(Deflater.NO_COMPRESSION) // аудіо й фото вже стиснуті
            media.forEach { (name, p) ->
                z.putNextEntry(ZipEntry(p))
                file(name).inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    /** Додає пісні з резервної копії (існуючі не перезаписуються). Повертає кількість нових пісень. */
    fun importFrom(uri: Uri): Int {
        var imported: Library? = null
        var pathToMedia: Map<String, String> = emptyMap()
        val extracted = mutableListOf<File>()
        fun extract(z: ZipInputStream, mediaName: String) {
            val f = File(mediaDir, File(mediaName).name)
            if (!f.exists()) {
                f.outputStream().use { z.copyTo(it) }
                extracted += f
            }
        }
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Не вдалося відкрити файл")
        ZipInputStream(BufferedInputStream(input)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                when {
                    e.name == "library.json" ->
                        imported = LibraryJson.decode(z.readBytes().toString(Charsets.UTF_8))
                    e.name == "files.json" -> {
                        val o = JSONObject(z.readBytes().toString(Charsets.UTF_8))
                        pathToMedia = o.keys().asSequence().associateBy({ o.getString(it) }, { it })
                    }
                    e.name.startsWith("media/") -> extract(z, e.name) // копії старих версій
                    else -> pathToMedia[e.name]?.let { extract(z, it) }
                }
            }
        }
        val lib = imported ?: run {
            extracted.forEach { it.delete() }
            error("Це не резервна копія «Моя музика»")
        }
        var added = 0
        update { cur ->
            val catIds = cur.categories.map { it.id }.toSet()
            val songIds = cur.songs.map { it.id }.toSet()
            val newSongs = lib.songs.filter { it.id !in songIds }
            added = newSongs.size
            cur.copy(
                categories = cur.categories + lib.categories.filter { it.id !in catIds },
                songs = cur.songs + newSongs,
            )
        }
        return added
    }
}

private fun safeName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_").trim().trimEnd('.').take(80).ifBlank { "без назви" }

private const val BACKUP_README = """Моя музика — резервна копія

Тут усе, що було в додатку, у звичайних файлах:
  Пісні/<Категорія>/<Пісня>/
      інфо.txt      — назва, виконавець, коли додано
      примітки.txt  — твої примітки з датами
      текст.txt     — текст пісні
      акорди.txt    — акорди й таби
      Голосові/     — твої записи (.m4a), назва починається з дати
      Музика/       — оригінали, мінусовки
      Ноти/         — фото нот, PDF, Guitar Pro

Усе це можна відкрити й без додатку: на комп'ютері чи будь-якому телефоні.

Щоб повернути все в додаток: ⋮ → Резервні копії → «Відновити з копії»
і вибрати цей .zip. Файли library.json і files.json потрібні саме для цього —
не видаляй їх.
"""
