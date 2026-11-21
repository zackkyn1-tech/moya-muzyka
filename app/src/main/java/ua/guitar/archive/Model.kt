package ua.guitar.archive

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

data class Recording(
    val id: String,
    val name: String,
    val file: String,
    val createdAt: Long,
    val durationMs: Long,
)

/** Будь-який доданий файл: аудіо, PDF, Guitar Pro тощо. */
data class Attachment(
    val id: String,
    val name: String,
    val file: String,
    val mime: String,
    val addedAt: Long,
)

/** Примітка-запис у щоденнику пісні (темп, стрій, що не виходить…). */
data class Note(
    val id: String,
    val text: String,
    val createdAt: Long,
)

/** Як сортувати пісні в категорії. */
enum class SongSort(val key: String) {
    MANUAL("manual"), ALPHA("alpha"), EASY_FIRST("easy"), HARD_FIRST("hard");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: MANUAL
    }
}

data class Category(
    val id: String,
    val name: String,
    val cover: String?,
    val createdAt: Long,
    val sort: SongSort = SongSort.MANUAL,
)

data class Song(
    val id: String,
    val categoryId: String,
    val title: String,
    val artist: String = "",
    val cover: String? = null,
    val lyrics: String = "",
    val chords: String = "",
    val noteImages: List<String> = emptyList(),
    val noteFiles: List<Attachment> = emptyList(),
    val audio: List<Attachment> = emptyList(),
    val recordings: List<Recording> = emptyList(),
    val notes: List<Note> = emptyList(),
    /** Складність 1..5, 0 — не вказано. */
    val difficulty: Int = 0,
    /** Позиція при ручному сортуванні. */
    val order: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun allFiles(): List<String> =
        listOfNotNull(cover) + noteImages + noteFiles.map { it.file } +
            audio.map { it.file } + recordings.map { it.file }
}

data class Library(
    val categories: List<Category> = emptyList(),
    val songs: List<Song> = emptyList(),
)

object LibraryJson {
    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)

    private fun att(a: Attachment) = JSONObject()
        .put("id", a.id).put("name", a.name).put("file", a.file)
        .put("mime", a.mime).put("addedAt", a.addedAt)

    private fun att(o: JSONObject) = Attachment(
        o.getString("id"), o.optString("name"), o.getString("file"),
        o.optString("mime", "application/octet-stream"), o.optLong("addedAt"),
    )

    private inline fun <T> JSONArray?.list(f: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { f(getJSONObject(it)) }
    }

    fun encode(l: Library): String {
        val cats = JSONArray()
        l.categories.forEach {
            cats.put(
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("cover", it.cover ?: JSONObject.NULL).put("createdAt", it.createdAt)
                    .put("sort", it.sort.key)
            )
        }
        val songs = JSONArray()
        l.songs.forEach { s ->
            songs.put(
                JSONObject()
                    .put("id", s.id).put("categoryId", s.categoryId)
                    .put("title", s.title).put("artist", s.artist)
                    .put("cover", s.cover ?: JSONObject.NULL)
                    .put("lyrics", s.lyrics).put("chords", s.chords)
                    .put("noteImages", JSONArray(s.noteImages))
                    .put("noteFiles", JSONArray().apply { s.noteFiles.forEach { put(att(it)) } })
                    .put("audio", JSONArray().apply { s.audio.forEach { put(att(it)) } })
                    .put("recordings", JSONArray().apply {
                        s.recordings.forEach {
                            put(
                                JSONObject().put("id", it.id).put("name", it.name)
                                    .put("file", it.file).put("createdAt", it.createdAt)
                                    .put("durationMs", it.durationMs)
                            )
                        }
                    })
                    .put("notes", JSONArray().apply {
                        s.notes.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("createdAt", it.createdAt)) }
                    })
                    .put("difficulty", s.difficulty)
                    .put("order", s.order)
                    .put("createdAt", s.createdAt)
            )
        }
        return JSONObject().put("version", 1).put("categories", cats).put("songs", songs).toString(1)
    }

    fun decode(text: String): Library {
        val o = JSONObject(text)
        val cats = o.optJSONArray("categories").list {
            Category(
                it.getString("id"), it.optString("name"), it.str("cover"), it.optLong("createdAt"),
                SongSort.of(it.str("sort")),
            )
        }
        val songs = o.optJSONArray("songs").list { s ->
            val imgs = s.optJSONArray("noteImages")
            Song(
                id = s.getString("id"),
                categoryId = s.getString("categoryId"),
                title = s.optString("title"),
                artist = s.optString("artist"),
                cover = s.str("cover"),
                lyrics = s.optString("lyrics"),
                chords = s.optString("chords"),
                noteImages = if (imgs == null) emptyList() else (0 until imgs.length()).map { imgs.getString(it) },
                noteFiles = s.optJSONArray("noteFiles").list(::att),
                audio = s.optJSONArray("audio").list(::att),
                recordings = s.optJSONArray("recordings").list {
                    Recording(
                        it.getString("id"), it.optString("name"), it.getString("file"),
                        it.optLong("createdAt"), it.optLong("durationMs"),
                    )
                },
                notes = s.optJSONArray("notes").list {
                    Note(it.getString("id"), it.optString("text"), it.optLong("createdAt"))
                },
                difficulty = s.optInt("difficulty").coerceIn(0, 5),
                order = s.optLong("order"),
                createdAt = s.optLong("createdAt"),
            )
        }
        return Library(cats, songs)
    }
}

fun difficultyName(level: Int): String = when (level) {
    1 -> "легко"
    2 -> "нескладно"
    3 -> "середньо"
    4 -> "складно"
    5 -> "дуже складно"
    else -> "не вказано"
}

fun sortSongs(songs: List<Song>, sort: SongSort): List<Song> = when (sort) {
    SongSort.MANUAL -> songs.sortedWith(compareBy<Song>({ it.order }, { it.createdAt }))
    SongSort.ALPHA -> songs.sortedBy { it.title.lowercase() }
    // Пісні без оцінки — завжди в кінці.
    SongSort.EASY_FIRST -> songs.sortedWith(compareBy<Song>({ if (it.difficulty == 0) 6 else it.difficulty }, { it.title.lowercase() }))
    SongSort.HARD_FIRST -> songs.sortedWith(compareBy<Song>({ -it.difficulty }, { it.title.lowercase() }))
}
