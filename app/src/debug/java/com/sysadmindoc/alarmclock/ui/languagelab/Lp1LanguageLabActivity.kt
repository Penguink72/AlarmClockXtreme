package com.sysadmindoc.alarmclock.ui.languagelab

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.alarmclock.ui.theme.AlarmClockXtremeTheme
import org.json.JSONObject

/**
 * Explicit debug-only LP1 experimental screen. It deliberately never changes
 * Android LocaleManager, normal UI resources, alarm services or history.
 */
class Lp1LanguageLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlarmClockXtremeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Lp1LabScreen(onClose = ::finish)
                }
            }
        }
    }
}

@Composable
private fun Lp1LabScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val catalogResult = remember { runCatching { Lp1Repository.loadCatalog(context) } }
    val catalog = catalogResult.getOrNull()
    if (catalog == null) {
        Column(Modifier.padding(24.dp)) {
            Text("語言包資料無法載入", color = MaterialTheme.colorScheme.error)
            Text(catalogResult.exceptionOrNull()?.message.orEmpty())
            Button(onClick = onClose) { Text("返回") }
        }
        return
    }
    var installed by remember { mutableStateOf(Lp1Repository.loadInstalled(context, catalog)) }
    var primary by rememberSaveable { mutableStateOf("zh-TW") }
    var secondary by rememberSaveable { mutableStateOf("ja") }
    var dual by rememberSaveable { mutableStateOf(true) }
    var key by rememberSaveable { mutableStateOf(catalog.entries.first().key) }
    var search by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var newLocale by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("此頁僅是 200 字串 Debug 實驗；不會替換正式 UI。") }
    val selected = catalog.byKey.getValue(key)
    val currentPack = installed[primary]
    val secondPack = installed[secondary]
    LaunchedEffect(primary, key, installed) {
        draft = installed[primary]?.translations?.get(key).orEmpty()
    }
    val formatValid = Lp1Repository.placeholders(draft) == selected.placeholders
    val resolvedPrimary = Lp1Repository.resolve(catalog, currentPack, key)
    val resolvedSecondary = Lp1Repository.resolve(catalog, secondPack, key)
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val attempt = runCatching {
                val stream = requireNotNull(context.contentResolver.openInputStream(uri))
                val raw = stream.use(Lp1Repository::readBounded)
                val pack = Lp1Repository.install(context, raw, catalog)
                installed = Lp1Repository.loadInstalled(context, catalog)
                primary = pack.locale
                pack
            }
            message = attempt.fold(
                onSuccess = { "已安裝 " + it.locale + "，有 " + it.translations.size + " / 200 筆翻譯" },
                onFailure = { "匯入被拒絕：" + (it.message ?: it.javaClass.simpleName) }
            )
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && currentPack != null) {
            val attempt = runCatching {
                val payload = packJson(catalog, currentPack)
                requireNotNull(context.contentResolver.openOutputStream(uri)).use {
                    it.write(payload.toByteArray(Charsets.UTF_8))
                }
            }
            message = if (attempt.isSuccess) "已匯出 " + primary else "匯出失敗：" + attempt.exceptionOrNull()?.message
        }
    }

    Scaffold(topBar = {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LP1 語言包實驗", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onClose) { Text("返回") }
        }
    }) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("200 個真實字串。支援離線 JSON 匯入、編輯、重啟後保存及雙語預覽。", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text("匯入 JSON")
                }
                OutlinedButton(onClick = { exportLauncher.launch("acx-lp1-" + primary + ".json") }) {
                    Text("匯出 " + primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = newLocale,
                    onValueChange = { newLocale = it },
                    singleLine = true,
                    label = { Text("新語言，如 ko-KR") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val locale = newLocale.trim()
                    val result = runCatching {
                        val content = JSONObject()
                            .put("format", "acx-language-pack-probe/1")
                            .put("catalogRevision", catalog.revision)
                            .put("locale", locale)
                            .put("packVersion", "0.1.0")
                            .put("translations", JSONObject()).toString()
                        Lp1Repository.install(context, content, catalog)
                    }
                    message = result.fold(
                        onSuccess = {
                            installed = Lp1Repository.loadInstalled(context, catalog)
                            primary = it.locale
                            "已建立空白語言包 " + it.locale
                        },
                        onFailure = { "無法建立：" + it.message }
                    )
                }) { Text("建立") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageSelector(
                    title = "主要語言",
                    selected = primary,
                    choices = installed.keys.toList(),
                    onChoose = { primary = it },
                    modifier = Modifier.weight(1f)
                )
                if (dual) LanguageSelector(
                    title = "對照語言",
                    selected = secondary,
                    choices = installed.keys.toList(),
                    onChoose = { secondary = it },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = dual, onCheckedChange = { dual = it })
                Text(if (dual) "雙語顯示" else "單語顯示")
            }
            val coverage = installed.entries.joinToString("  ·  ") { it.key + " " + it.value.translations.size + "/200" }
            Text(coverage, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("搜尋 Key 或英文原文") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            val filtered = remember(search, catalog) {
                catalog.entries.filter {
                    it.key.contains(search, ignoreCase = true) || it.source.contains(search, ignoreCase = true)
                }
            }
            Text("字串項目：" + filtered.size, style = MaterialTheme.typography.bodySmall)
            LazyColumn(modifier = Modifier.height(205.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filtered, key = { it.key }) { entry ->
                    TextButton(onClick = { key = entry.key }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (entry.key == key) "● " else "") + entry.key + " · " + entry.section)
                    }
                }
            }
            HorizontalDivider()
            Text("Key：" + key, fontWeight = FontWeight.Medium)
            Text("原文：" + selected.source, style = MaterialTheme.typography.bodyMedium)
            Text(
                "格式參數：" + (if (selected.placeholders.isEmpty()) "無" else selected.placeholders.joinToString("、")),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(primary + " 翻譯") },
                isError = !formatValid,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            if (!formatValid) Text("變數格式不符，無法套用", color = MaterialTheme.colorScheme.error)
            Button(enabled = formatValid, onClick = {
                val entries = currentPack?.translations.orEmpty().toMutableMap()
                if (draft.isBlank()) entries.remove(key) else entries[key] = draft
                val temp = Lp1Pack(primary, entries, "editor", custom = true)
                val outcome = runCatching {
                    Lp1Repository.install(context, packJson(catalog, temp), catalog)
                }
                message = outcome.fold(
                    onSuccess = {
                        installed = Lp1Repository.loadInstalled(context, catalog)
                        "已儲存 " + key
                    },
                    onFailure = { "儲存失敗：" + it.message }
                )
            }) { Text("儲存這條翻譯") }

            Text("即時預覽", style = MaterialTheme.typography.titleMedium)
            val primaryPreview = if (draft.isNotBlank() && formatValid) draft else resolvedPrimary.value
            Text(primaryPreview, fontWeight = FontWeight.Bold)
            if (resolvedPrimary.fallback && draft.isBlank()) Text("主要語言缺漏：使用英文備援", style = MaterialTheme.typography.bodySmall)
            if (dual) {
                Text(resolvedSecondary.value, color = MaterialTheme.colorScheme.secondary)
                if (resolvedSecondary.fallback) Text("對照語言缺漏：使用英文備援", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text(message, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            Text("不會更改已儲存鬧鐘、響鈴服務、通知與正式語系。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun packJson(catalog: Lp1Catalog, pack: Lp1Pack): String =
    JSONObject()
        .put("format", "acx-language-pack-probe/1")
        .put("catalogRevision", catalog.revision)
        .put("locale", pack.locale)
        .put("packVersion", pack.version)
        .put("translations", JSONObject(pack.translations)).toString(2)

@Composable
private fun LanguageSelector(
    title: String,
    selected: String,
    choices: List<String>,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { expanded = true }) { Text(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { locale ->
                DropdownMenuItem(text = { Text(locale) }, onClick = {
                    onChoose(locale)
                    expanded = false
                })
            }
        }
    }
}
