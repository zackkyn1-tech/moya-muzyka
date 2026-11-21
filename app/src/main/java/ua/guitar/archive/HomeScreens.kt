@file:OptIn(ExperimentalMaterial3Api::class)

package ua.guitar.archive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Зберігає нову обкладинку (якщо вибрана) і повертає ім'я файлу, яке треба записати. */
fun applyCover(vm: AppVm, old: String?, newCover: Uri?, remove: Boolean, save: (String?) -> Unit) {
    when {
        newCover != null -> vm.importFiles(listOf(newCover)) { res ->
            res.firstOrNull()?.let { save(it.file); vm.store.deleteFiles(listOf(old)) }
        }
        remove -> {
            save(null); vm.store.deleteFiles(listOf(old))
        }
    }
}

@Composable
fun HomeScreen(vm: AppVm, lib: Library) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            query, { query = it },
                            placeholder = { Text("Пошук пісні…") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else Text("Схрон", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    if (searching) IconButton(onClick = { searching = false; query = "" }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, "Пошук") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Меню") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Резервні копії") },
                                    leadingIcon = { Icon(Icons.Rounded.Backup, null) },
                                    onClick = { menu = false; backups = true })
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Перевірити оновлення") },
                                    leadingIcon = { Icon(Icons.Rounded.SystemUpdate, null) },
                                    onClick = { menu = false; vm.checkUpdates(manual = true) })
                                DropdownMenuItem(
                                    text = { Text("Версія ${BuildConfig.VERSION_NAME}") },
                                    enabled = false,
                                    onClick = {})
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!searching) ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Категорія") },
            )
        },
    ) { pad ->
        when {
            searching -> SearchResults(vm, lib, query, pad)
            lib.categories.isEmpty() -> EmptyState(
                Icons.Rounded.LibraryMusic,
                "Поки що порожньо",
                "Створи першу категорію — наприклад «Рок» — і додавай туди пісні, тексти, ноти та свої записи.",
                Modifier.padding(pad).padding(top = 48.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = pad.calculateTopPadding() + 4.dp,
                    bottom = pad.calculateBottomPadding() + 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (vm.backupFolder == null || vm.backupFailed) item(span = { GridItemSpan(maxLineSpan) }) {
                    BackupBanner(vm.backupFailed) { backups = true }
                }
                items(lib.categories, key = { it.id }) { c ->
                    CategoryCard(
                        vm, c, lib.songs.count { it.categoryId == c.id },
                        onEdit = { editing = c }, onDelete = { deleting = c },
                    )
                }
            }
        }
    }

    if (backups) BackupDialog(vm) { backups = false }

    if (adding) EditItemDialog(
        title = "Нова категорія", nameLabel = "Назва (напр. Рок)", initialName = "",
        secondLabel = null, initialSecond = "", initialCover = null,
        onDismiss = { adding = false },
    ) { name, _, cover, _ ->
        val id = newId()
        vm.store.update { it.copy(categories = it.categories + Category(id, name, null, System.currentTimeMillis())) }
        applyCover(vm, null, cover, false) { f -> vm.store.updateCategory(id) { it.copy(cover = f) } }
    }
    editing?.let { c ->
        EditItemDialog(
            title = "Редагувати категорію", nameLabel = "Назва", initialName = c.name,
            secondLabel = null, initialSecond = "", initialCover = c.cover?.let(vm.store::file),
            onDismiss = { editing = null },
        ) { name, _, cover, remove ->
            vm.store.updateCategory(c.id) { it.copy(name = name) }
            applyCover(vm, c.cover, cover, remove) { f -> vm.store.updateCategory(c.id) { it.copy(cover = f) } }
        }
    }
    deleting?.let { c ->
        ConfirmDialog(
            "Видалити «${c.name}»?",
            "Усі пісні цієї категорії разом із записами та файлами буде видалено назавжди.",
            onDismiss = { deleting = null },
        ) { vm.player.release(); vm.store.deleteCategory(c.id) }
    }
}

@Composable
private fun CategoryCard(vm: AppVm, c: Category, count: Int, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(MaterialTheme.shapes.large)
            .clickable { vm.open(Screen.CategoryScr(c.id)) },
        shape = MaterialTheme.shapes.large,
    ) {
        Box {
            CoverImage(c.cover?.let(vm.store::file), Modifier.fillMaxSize(), icon = Icons.Rounded.LibraryMusic)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
            )
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(songsCount(count), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { menu = true },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                    modifier = Modifier.padding(4.dp),
                ) { Icon(Icons.Rounded.MoreVert, "Меню", tint = Color.White) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Редагувати") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text("Видалити") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun SearchResults(vm: AppVm, lib: Library, query: String, pad: PaddingValues) {
    val q = query.trim().lowercase()
    val found = if (q.isEmpty()) emptyList() else lib.songs.filter {
        it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) ||
            it.lyrics.lowercase().contains(q) || it.chords.lowercase().contains(q) ||
            it.notes.any { n -> n.text.lowercase().contains(q) }
    }.sortedBy { it.title.lowercase() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        if (q.isNotEmpty() && found.isEmpty()) item {
            EmptyState(Icons.Rounded.SearchOff, "Нічого не знайдено", "Спробуй іншу назву або виконавця")
        }
        items(found, key = { it.id }) { s ->
            val cat = lib.categories.find { it.id == s.categoryId }
            SongListItem(vm, s, cat, showCategory = true)
        }
    }
}

@Composable
fun SongListItem(
    vm: AppVm,
    s: Song,
    cat: Category?,
    showCategory: Boolean = false,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val sub = buildList {
        if (s.artist.isNotBlank()) add(s.artist)
        if (showCategory && cat != null) add(cat.name)
        if (s.recordings.isNotEmpty()) add("🎙 ${s.recordings.size}")
    }.joinToString(" · ")
    ListItem(
        headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                if (sub.isNotEmpty()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.difficulty > 0) Box(Modifier.padding(top = 3.dp)) { DifficultyStars(s.difficulty) }
            }
        },
        leadingContent = {
            CoverImage(
                (s.cover ?: cat?.cover)?.let(vm.store::file),
                Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), reqPx = 160,
            )
        },
        trailingContent = trailing,
        modifier = modifier.clickable { vm.open(Screen.SongScr(s.id)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SortChips(current: SongSort, onSelect: (SongSort) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = current == SongSort.MANUAL,
            onClick = { onSelect(SongSort.MANUAL) },
            label = { Text("Вручну") },
            leadingIcon = { Icon(Icons.Rounded.DragIndicator, null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = current == SongSort.ALPHA,
            onClick = { onSelect(SongSort.ALPHA) },
            label = { Text("А–Я") },
            leadingIcon = { Icon(Icons.Rounded.SortByAlpha, null, Modifier.size(18.dp)) },
        )
        val byDiff = current == SongSort.EASY_FIRST || current == SongSort.HARD_FIRST
        FilterChip(
            selected = byDiff,
            // Повторне натискання міняє напрямок: легкі ↔ складні спочатку.
            onClick = { onSelect(if (current == SongSort.EASY_FIRST) SongSort.HARD_FIRST else SongSort.EASY_FIRST) },
            label = {
                Text(
                    when (current) {
                        SongSort.HARD_FIRST -> "Складні спочатку"
                        SongSort.EASY_FIRST -> "Легкі спочатку"
                        else -> "Складність"
                    }
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Star, null, Modifier.size(18.dp),
                    tint = when (current) {
                        SongSort.HARD_FIRST -> difficultyColor(5)
                        SongSort.EASY_FIRST -> difficultyColor(1)
                        else -> LocalContentColor.current
                    },
                )
            },
        )
    }
}

@Composable
fun CategoryScreen(vm: AppVm, lib: Library, cat: Category) {
    val sorted = sortSongs(lib.songs.filter { it.categoryId == cat.id }, cat.sort)
    val manual = cat.sort == SongSort.MANUAL
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // ---- Перетягування (ручне сортування) ----
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var working by remember { mutableStateOf(sorted) }
    if (draggingId == null && working != sorted) working = sorted
    val songs = if (draggingId != null) working else sorted

    fun onDrag(dy: Float) {
        val id = draggingId ?: return
        dragOffset += dy
        val info = listState.layoutInfo
        val cur = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val center = cur.offset + dragOffset + cur.size / 2f
        val ids = working.map { it.id }.toSet()
        val target = info.visibleItemsInfo.firstOrNull {
            it.key != id && it.key in ids && center >= it.offset && center <= it.offset + it.size
        }
        if (target != null) {
            val from = working.indexOfFirst { it.id == id }
            val to = working.indexOfFirst { it.id == target.key }
            if (from >= 0 && to >= 0) {
                working = working.toMutableList().apply { add(to, removeAt(from)) }
                dragOffset += cur.offset - target.offset
            }
        }
        // Автопрокрутка, коли тягнеш до краю екрана.
        val edge = 120f
        val delta = when {
            center < info.viewportStartOffset + edge -> -18f
            center > info.viewportEndOffset - edge -> 18f
            else -> 0f
        }
        if (delta != 0f) dragOffset += listState.dispatchRawDelta(delta)
    }

    fun endDrag() {
        if (draggingId == null) return
        val order = working.mapIndexed { i, s -> s.id to i.toLong() }.toMap()
        draggingId = null
        dragOffset = 0f
        vm.store.update { l -> l.copy(songs = l.songs.map { s -> order[s.id]?.let { s.copy(order = it) } ?: s }) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(cat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, "Редагувати") }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, "Видалити") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Пісня") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 96.dp),
        ) {
            if (cat.cover != null) item(key = "_cover") {
                CoverImage(
                    vm.store.file(cat.cover),
                    Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp)),
                    reqPx = 1024,
                )
            }
            if (songs.isEmpty()) item(key = "_empty") {
                EmptyState(Icons.Rounded.MusicNote, "Тут ще немає пісень", "Натисни «Пісня», щоб додати першу.")
            } else item(key = "_header") {
                Column(Modifier.padding(top = 12.dp)) {
                    SortChips(cat.sort) { new -> vm.store.updateCategory(cat.id) { it.copy(sort = new) } }
                    Text(
                        songsCount(songs.size) + if (manual) " · тягни за ⠿, щоб змінити порядок" else "",
                        Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(songs, key = { it.id }) { s ->
                val dragging = s.id == draggingId
                val itemModifier = if (dragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = dragOffset; shadowElevation = 16f }
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                } else Modifier.animateItem()
                SongListItem(
                    vm, s, cat, modifier = itemModifier,
                    trailing = if (manual) ({
                        Icon(
                            Icons.Rounded.DragIndicator, "Перетягнути",
                            tint = if (dragging) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .padding(8.dp)
                                .pointerInput(s.id) {
                                    detectDragGestures(
                                        onDragStart = { draggingId = s.id; dragOffset = 0f },
                                        onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
                                        onDragEnd = { endDrag() },
                                        onDragCancel = { endDrag() },
                                    )
                                },
                        )
                    }) else null,
                )
            }
        }
    }

    if (adding) EditItemDialog(
        title = "Нова пісня", nameLabel = "Назва пісні", initialName = "",
        secondLabel = "Виконавець (необов'язково)", initialSecond = "", initialCover = null,
        onDismiss = { adding = false },
    ) { name, artist, cover, _ ->
        val id = newId()
        val nextOrder = (lib.songs.filter { it.categoryId == cat.id }.maxOfOrNull { it.order } ?: 0L) + 1
        vm.store.update {
            it.copy(songs = it.songs + Song(id = id, categoryId = cat.id, title = name, artist = artist, order = nextOrder))
        }
        applyCover(vm, null, cover, false) { f -> vm.store.updateSong(id) { it.copy(cover = f) } }
        vm.open(Screen.SongScr(id))
    }
    if (editing) EditItemDialog(
        title = "Редагувати категорію", nameLabel = "Назва", initialName = cat.name,
        secondLabel = null, initialSecond = "", initialCover = cat.cover?.let(vm.store::file),
        onDismiss = { editing = false },
    ) { name, _, cover, remove ->
        vm.store.updateCategory(cat.id) { it.copy(name = name) }
        applyCover(vm, cat.cover, cover, remove) { f -> vm.store.updateCategory(cat.id) { it.copy(cover = f) } }
    }
    if (deleting) ConfirmDialog(
        "Видалити «${cat.name}»?",
        "Усі пісні цієї категорії разом із записами та файлами буде видалено назавжди.",
        onDismiss = { deleting = false },
    ) { vm.player.release(); vm.store.deleteCategory(cat.id); vm.back() }
}

@Composable
private fun BackupBanner(failed: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (failed) Icons.Rounded.Warning else Icons.Rounded.Backup, null, tint = Accent)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    if (failed) "Остання копія не збереглась" else "Налаштуй автоматичні копії",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (failed) "Натисни, щоб вибрати папку ще раз"
                    else "Щоб і через роки нічого не загубилось, навіть якщо щось станеться з додатком",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BackupDialog(vm: AppVm, onDismiss: () -> Unit) {
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let(vm::chooseBackupFolder)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        it?.let(vm::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(vm::importBackup)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Backup, null) },
        title = { Text("Резервні копії") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Усе зберігається тільки на цьому телефоні — нічого не йде в інтернет чи хмару.\n\n" +
                        "Щоб не втратити пісні й записи, додаток сам кладе копію (.zip) у вибрану папку на телефоні, " +
                        "коли ти щось змінив і закрив додаток. Зберігаються $KEEP_BACKUPS останніх копій. " +
                        "Копію можна відкрити навіть без додатку — там звичайні папки з текстами й записами.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val folder = vm.backupFolderName()
                Text(
                    "Папка: " + (folder ?: "не вибрана"),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (folder == null) MaterialTheme.colorScheme.error else Accent,
                )
                if (vm.lastBackupAt > 0) Text(
                    "Остання копія: ${fmtDate(vm.lastBackupAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (vm.backupFailed) Text(
                    "Остання автоматична копія не вдалася. Вибери папку ще раз.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
                FilledTonalButton(onClick = { pickFolder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Folder, null); Spacer(Modifier.width(8.dp))
                    Text(if (folder == null) "Вибрати папку" else "Змінити папку")
                }
                FilledTonalButton(
                    onClick = { vm.backupNow(manual = true) },
                    enabled = folder != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Rounded.Backup, null); Spacer(Modifier.width(8.dp)); Text("Зробити копію зараз") }
                OutlinedButton(
                    onClick = {
                        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportLauncher.launch("moya-muzyka-kopiya-$d.zip")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Rounded.Upload, null); Spacer(Modifier.width(8.dp)); Text("Зберегти копію в інше місце…") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(8.dp)); Text("Відновити з копії…") }
                Text(
                    "Порада: раз на кілька місяців скидай свіжу копію на комп'ютер або флешку — " +
                        "тоді пісні переживуть навіть втрату телефону.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрити") } },
    )
}
