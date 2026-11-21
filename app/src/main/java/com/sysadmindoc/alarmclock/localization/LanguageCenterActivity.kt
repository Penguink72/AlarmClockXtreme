package com.sysadmindoc.alarmclock.localization

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.alarmclock.localization.LanguagePackV1Repository
import com.sysadmindoc.alarmclock.R
import androidx.compose.ui.res.stringResource
import com.sysadmindoc.alarmclock.ui.theme.AlarmClockXtremeTheme
import com.sysadmindoc.alarmclock.util.LocaleSelectionContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * B1-U1: Release-compatible offline pack authoring / review activity.
 * It does not change UI/AlarmManager locale; B0-L1 integration follows.
 * No interpreter executes imported text or handles private user media.
 */
class LanguageCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlarmClockXtremeTheme {
                LanguageCenterScreen(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun LanguageCenterScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<LanguageCenterSnapshot?>(null) }
    var repository by remember { mutableStateOf<LanguagePackV1Repository?>(null) }
    var initialError by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var chosenLocale by rememberSaveable { mutableStateOf("") }
    var newLocale by rememberSaveable { mutableStateOf("ko-KR") }
    var current by remember { mutableStateOf<LanguagePackReadResult?>(null) }
    var status by remember { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var textDraft by remember { mutableStateOf("") }
    var pluralDraft by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var arrayDraft by remember { mutableStateOf<List<String>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    val loaded = snapshot
    val labels = loaded?.labels
    val store = repository

    LaunchedEffect(context) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val loaded = LanguageCenterData.load(context)
                val store = LanguagePackV1Repository.forApp(context, loaded.catalog)
                Triple(loaded,store,store.installedLocales())
            }
        }
        result.onSuccess { (loaded,store,found) ->
            snapshot = loaded
            repository = store
            installed = found
            val chosen = chosenLocale.takeIf { it in found } ?: found.sorted().firstOrNull().orEmpty()
            chosenLocale = chosen
            current = if (chosen.isEmpty()) null else withContext(Dispatchers.IO) { store.load(chosen) }
        }.onFailure { initialError = it.message ?: it.javaClass.simpleName }
    }

    // Rebind drafts to the selected key + current stored revision. This avoids
    // accidentally saving the prior key's translation after switching items.
    LaunchedEffect(selectedKey,chosenLocale,current) {
        val key = selectedKey ?: return@LaunchedEffect
        val spec = loaded?.catalog?.sources?.get(key) ?: return@LaunchedEffect
        val entry = current?.validation?.pack?.entries?.get(key)
        textDraft = entry?.text.orEmpty()
        pluralDraft = entry?.forms ?: mapOf("other" to "")
        arrayDraft = entry?.items ?: List(spec.arrayItemCount) { "" }
    }

    fun reloadPack(locale: String) {
        val targetStore = store ?: return
        scope.launch {
            val state = withContext(Dispatchers.IO) {
                val available = targetStore.installedLocales()
                val chosen = locale.takeIf { it in available }
                    ?: available.sorted().firstOrNull().orEmpty()
                Triple(available,chosen,if (chosen.isEmpty()) null else targetStore.load(chosen))
            }
            installed=state.first
            chosenLocale=state.second
            current=state.third
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && store != null) {
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = requireNotNull(context.contentResolver.openInputStream(uri))
                        store.importFromStream(stream)
                    }
                }
                outcome.onSuccess { result ->
                    status = if (result.stored) {
                        labels?.get("imported").orEmpty() + " " + result.locale
                    } else {
                        labels?.get("failed").orEmpty() + " " +
                            (result.validation?.errors?.joinToString() ?: result.errorCode)
                    }
                    if (result.stored && result.locale != null) reloadPack(result.locale)
                }.onFailure { status = labels?.get("failed").orEmpty() + " " + it.javaClass.simpleName }
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && store != null && chosenLocale.isNotEmpty()) {
            val requested = chosenLocale
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = requireNotNull(context.contentResolver.openOutputStream(uri))
                        output.use { store.exportToStream(requested,it) }
                    }.getOrDefault(false)
                }
                status = (labels?.get(if (success) "exported" else "failed") ?: "") + " " + requested
            }
        }
    }

    val filtered = remember(loaded,search) {
        loaded?.compare?.let { data ->
            if (search.isBlank()) data else data.filter { entry ->
                val query = search.trim()
                entry.key.contains(query,ignoreCase=true) ||
                    entry.english.contains(query,ignoreCase=true) ||
                    entry.traditionalChinese?.contains(query,ignoreCase=true)==true ||
                    entry.japanese?.contains(query,ignoreCase=true)==true
            }
        } ?: emptyList()
    }
    val comparison = remember(loaded,selectedKey) {
        loaded?.compare?.firstOrNull { it.key == selectedKey }
    }
    val spec = selectedKey?.let { loaded?.catalog?.sources?.get(it) }
    val statusCode = current?.validation
    val existing = selectedKey?.let { statusCode?.pack?.entries?.get(it) }
    val canEdit = spec?.translatable==true &&
        !(spec.type=="string"&&spec.hasRichMarkup)
    val canSave = chosenLocale.isNotBlank() && canEdit && when (spec?.type) {
        "string" -> textDraft.isNotBlank()
        "plurals" -> pluralDraft["other"]?.isNotBlank()==true
        "array" -> arrayDraft.size == spec.arrayItemCount && arrayDraft.all { it.isNotBlank() }
        else -> false
    }

    fun saveEntry(remove: Boolean = false) {
        val source = spec ?: return
        val targetStore = store ?: return
        val loadedCatalog = loaded?.catalog ?: return
        val locale = chosenLocale.takeIf { it.isNotBlank() } ?: return
        val replacement = if (remove) null else when (source.type) {
            "string" -> LanguagePackV1Entry(source.sourceHash,source.type,text=textDraft)
            "plurals" -> LanguagePackV1Entry(source.sourceHash,source.type,
                forms=pluralDraft.filterValues { it.isNotBlank() })
            "array" -> LanguagePackV1Entry(source.sourceHash,source.type,items=arrayDraft)
            else -> null
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val payload = LanguageCenterData.assemblePack(
                        loadedCatalog,locale,current?.validation?.pack,source.key,replacement
                    )
                    targetStore.importRaw(payload)
                }
            }
            result.onSuccess { outcome ->
                if (outcome.stored) {
                    status = labels?.get(if(remove)"removed" else "saved").orEmpty() + " " + source.key
                    reloadPack(locale)
                } else {
                    status = (labels?.get("failed") ?: "") + " " +
                        (outcome.validation?.errors?.joinToString() ?: outcome.errorCode)
                }
            }.onFailure { status = (labels?.get("failed") ?: "") + " " + it.javaClass.simpleName }
        }
    }

    Scaffold(topBar = {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement=Arrangement.SpaceBetween) {
            Text(labels?.get("title") ?: stringResource(R.string.settings_language),style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onClose) { Text(labels?.get("back") ?: stringResource(R.string.cancel)) }
        }
    }) { insets ->
        if (loaded == null) {
            Column(Modifier.fillMaxSize().padding(insets).padding(20.dp)) {
                if (initialError == null) CircularProgressIndicator()
                Text(initialError ?: stringResource(R.string.settings_title))
            }
        } else {
            LazyColumn(
                modifier=Modifier.fillMaxSize().padding(insets).padding(horizontal=12.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)
            ) {
                item("notice") {
                    Text(labels?.get("sub").orEmpty(),style=MaterialTheme.typography.bodyMedium)
                    Text(labels?.get("warning").orEmpty(),
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                    Text(labels?.get("unreviewed").orEmpty(),style=MaterialTheme.typography.labelSmall)
                    if(status.isNotEmpty()) Text(status,style=MaterialTheme.typography.bodyMedium)
                }
                item("pack-actions") {
                    OutlinedTextField(
                        value=newLocale,onValueChange={newLocale=it},singleLine=true,
                        label={Text(labels?.get("locale").orEmpty())},
                        modifier=Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick={
                            val canonical=LocaleSelectionContract.canonicalTag(newLocale)
                            if(canonical == null || store == null) {
                                status=(labels?.get("failed") ?: "") + " INVALID_LOCALE_TAG"
                            } else {
                                scope.launch {
                                    val result=withContext(Dispatchers.IO) {
                                        runCatching {
                                            store.importRaw(LanguageCenterData.assemblePack(
                                                loaded.catalog,canonical,store.load(canonical)?.validation?.pack
                                            ))
                                        }
                                    }
                                    result.onSuccess {
                                        if(it.stored) {
                                            status=(labels?.get("saved") ?: "") + " " + canonical
                                            reloadPack(canonical)
                                        } else status=(labels?.get("failed") ?: "") + " " +
                                            (it.validation?.errors?.joinToString() ?: it.errorCode)
                                    }.onFailure { status=(labels?.get("failed") ?: "") + " " + it.javaClass.simpleName }
                                }
                            }
                        }) { Text(labels?.get("newPack").orEmpty()) }
                        OutlinedButton(onClick={importer.launch(arrayOf("application/json","text/plain"))}) {
                            Text(labels?.get("import").orEmpty())
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={menuOpen=true}) {
                            Text((labels?.get("pack") ?: "") + ": " +
                                chosenLocale.ifEmpty { labels?.get("empty").orEmpty() })
                        }
                        DropdownMenu(expanded=menuOpen,onDismissRequest={menuOpen=false}) {
                            installed.sorted().forEach { tag ->
                                DropdownMenuItem(text={Text(tag)},onClick={
                                    menuOpen=false
                                    reloadPack(tag)
                                })
                            }
                        }
                        OutlinedButton(
                            enabled=chosenLocale.isNotBlank(),
                            onClick={exporter.launch("acx-language-"+chosenLocale+".json")}
                        ) { Text(labels?.get("export").orEmpty()) }
                    }
                    OutlinedButton(enabled=chosenLocale.isNotBlank(),
                        onClick={
                            val currentTag=chosenLocale
                            scope.launch {
                                val success=withContext(Dispatchers.IO) {
                                    store?.rollback(currentTag)==true
                                }
                                status=(labels?.get(if(success)"restore" else "failed") ?: "")+" "+currentTag
                                if(success)reloadPack(currentTag)
                            }
                        }) { Text(labels?.get("rollback").orEmpty()) }
                    if(current?.recoveredFromPrevious==true)
                        Text(labels?.get("recovered").orEmpty(),color=MaterialTheme.colorScheme.error)
                    if(statusCode!=null)
                        Text(
                            (labels?.get("usable") ?: "") + ": "+statusCode.usableKeys.size +
                                " · "+(labels?.get("stale") ?: "")+": "+statusCode.staleKeys.size+
                                " · "+(labels?.get("fallback") ?: "")+": "+statusCode.fallbackKeys.size,
                            style=MaterialTheme.typography.bodySmall
                        )
                }
                item("search") {
                    OutlinedTextField(value=search,onValueChange={search=it},singleLine=true,
                        label={Text(labels?.get("search").orEmpty())},
                        modifier=Modifier.fillMaxWidth())
                    Text(filtered.size.toString()+" / "+loaded.compare.size,
                        style=MaterialTheme.typography.labelMedium)
                }
                if (comparison != null && spec != null) {
                    item("editor") {
                        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            HorizontalDivider()
                            Text(comparison.key,style=MaterialTheme.typography.titleMedium)
                            Text(labels?.get("source").orEmpty(),style=MaterialTheme.typography.labelSmall)
                            Text(comparison.english)
                            Text(labels?.get("zh").orEmpty(),style=MaterialTheme.typography.labelSmall)
                            Text(comparison.traditionalChinese ?: labels?.get("missing").orEmpty())
                            Text(labels?.get("ja").orEmpty(),style=MaterialTheme.typography.labelSmall)
                            Text(comparison.japanese ?: labels?.get("missing").orEmpty())
                            if (spec.formatTokens.isNotEmpty())
                                Text((labels?.get("format") ?: "")+" : "+spec.formatTokens.joinToString(),
                                    color=MaterialTheme.colorScheme.error)
                            if (!canEdit) Text(
                                labels?.get(if(spec.hasRichMarkup)"rich" else "blocked").orEmpty(),
                                color=MaterialTheme.colorScheme.error
                            )
                            if(chosenLocale.isNotBlank()&&canEdit) {
                                when(spec.type) {
                                    "string" -> OutlinedTextField(
                                        value=textDraft,onValueChange={textDraft=it},
                                        label={Text(labels?.get("value").orEmpty())},
                                        modifier=Modifier.fillMaxWidth()
                                    )
                                    "plurals" -> {
                                        Text(labels?.get("plural").orEmpty())
                                        listOf("zero","one","two","few","many","other").forEach { kind ->
                                            OutlinedTextField(
                                                value=pluralDraft[kind].orEmpty(),
                                                onValueChange={value->
                                                    pluralDraft=pluralDraft+(kind to value)
                                                },
                                                label={Text(kind)},modifier=Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    "array" -> {
                                        Text(labels?.get("array").orEmpty())
                                        arrayDraft.forEachIndexed { index, value ->
                                            OutlinedTextField(
                                                value=value,onValueChange={new->
                                                    arrayDraft=arrayDraft.toMutableList().also { it[index]=new }
                                                },
                                                label={Text((index+1).toString())},
                                                modifier=Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Button(enabled=canSave,onClick={saveEntry()}) {
                                        Text(labels?.get("save").orEmpty())
                                    }
                                    OutlinedButton(enabled=existing!=null,onClick={saveEntry(remove=true)}) {
                                        Text(labels?.get("remove").orEmpty())
                                    }
                                }
                            } else if(chosenLocale.isEmpty()){
                                Text(labels?.get("notAvailable").orEmpty())
                            }
                            HorizontalDivider()
                        }
                    }
                }
                if(filtered.isEmpty()) {
                    item("empty") { Text(labels?.get("emptyQuery").orEmpty()) }
                }
                items(filtered,key={it.key}) { entry ->
                    Column(Modifier.fillMaxWidth().clickable { selectedKey=entry.key }.padding(vertical=8.dp)) {
                        Text(entry.key,style=MaterialTheme.typography.bodyMedium,
                            color=if(selectedKey==entry.key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(entry.english,maxLines=2,overflow=TextOverflow.Ellipsis,
                            style=MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
