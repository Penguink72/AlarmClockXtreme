"use strict";
(function () {
  const data = window.ACX_LP0_DATA;
  if (!data || !data.catalog || !data.packs) throw new Error("Missing trusted LP0 catalogue");
  const catalog = data.catalog;
  const entries = catalog.entries;
  const known = new Map(entries.map((entry) => [entry.key, entry]));
  const packs = Object.create(null);
  for (const [locale, pack] of Object.entries(data.packs)) {
    packs[locale] = JSON.parse(JSON.stringify(pack));
  }
  const el = (id) => document.getElementById(id);
  const $ = (id) => el(id);
  const sections = {alarm:"鬧鐘與響鈴",snooze:"貪睡",music:"音樂與鈴聲",history:"播放歷史",settings:"設定與導覽",diagnostics:"診斷及權限"};
  let selectedKey = entries[0].key;
  let primary = "zh-TW", secondary = "ja", mode = "dual";
  const core = window.ACX_LP0_CORE;
  if (!core) throw new Error("Missing pure LP0 locale pack core");
  const placeholders = core.placeholders;
  const same = core.same;
  const supportedLocale = core.supportedLocale;
  const validatePack = (candidate) => core.validatePack(candidate, catalog);
  const translate = (locale, key) => core.translate(packs, known, locale, key);
  const completion = (locale) => core.completion(packs, known, locale);
  function setMessage(msg, className) { $("message").textContent = msg; $("message").className = className || "muted"; }
  function refreshLocales() {
    const all = Object.keys(packs);
    for (const [select, value] of [[$("primary"),primary],[$("secondary"),secondary]]) {
      select.replaceChildren();
      for (const locale of all) {
        const o = document.createElement("option");
        o.value = locale; o.textContent = locale + " (" + completion(locale) + "/200)";
        select.append(o);
      }
      select.value = value;
    }
    $("secondaryBox").style.display = mode === "dual" ? "" : "none";
    $("mode").value = mode;
    $("coverage").replaceChildren();
    for (const locale of all) {
      const pill = document.createElement("span"); pill.className = "pill";
      pill.textContent = locale + ": " + completion(locale) + "/200";
      $("coverage").append(pill);
    }
  }
  function ensureTwo() {
    if (primary === secondary) {
      secondary = Object.keys(packs).find((x) => x !== primary) || primary;
    }
  }
  function renderPreview() {
    const entry = known.get(selectedKey);
    const a = translate(primary, selectedKey), b = translate(secondary, selectedKey);
    $("key").textContent = entry.key + "　·　" + sections[entry.section];
    $("source").textContent = entry.source;
    $("translation").value = packs[primary].translations[selectedKey] || "";
    $("translation").placeholder = "尚無翻譯；顯示時將備援英文";
    $("placeholder").textContent = entry.placeholders.length ? entry.placeholders.join(", ") : "無變數；可自由修改譯文";
    $("previewPrimary").textContent = a.value;
    $("previewSecondary").textContent = b.value;
    $("previewSecondary").style.display = mode === "dual" ? "" : "none";
    $("translationStatus").textContent = (a.fallback ? "主要語言缺漏：目前使用英文備援。" : "主要語言已提供翻譯。") +
      (mode === "dual" && b.fallback ? " 對照語言缺漏：使用英文備援。" : "");
  }
  function renderList() {
    const search = $("search").value.trim().toLocaleLowerCase();
    const category = $("category").value;
    const missingOnly = $("missingOnly").checked;
    const rows = entries.filter((e) => {
      if (category && e.section !== category) return false;
      if (missingOnly && packs[primary].translations[e.key]) return false;
      if (!search) return true;
      return e.key.toLowerCase().includes(search) || e.source.toLocaleLowerCase().includes(search) ||
        Object.values(packs).some((pack) => (pack.translations[e.key] || "").toLocaleLowerCase().includes(search));
    });
    $("visibleCount").textContent = "顯示 " + rows.length + " / 200 筆";
    $("keyList").replaceChildren();
    for (const e of rows) {
      const button = document.createElement("button");
      button.type = "button";
      if (selectedKey === e.key) button.className = "active";
      button.textContent = e.key;
      const detail = document.createElement("span"); detail.className = "sub";
      const tr = translate(primary,e.key);
      detail.textContent = sections[e.section] + " · " + (tr.fallback ? "英文備援" : "已翻譯");
      button.append(detail);
      button.addEventListener("click", () => { selectedKey = e.key; renderList(); renderPreview(); });
      $("keyList").append(button);
    }
  }
  function redraw() { refreshLocales(); renderList(); renderPreview(); }
  $("mode").addEventListener("change", (event) => { mode=event.target.value; redraw(); });
  $("primary").addEventListener("change", (event) => { primary=event.target.value; ensureTwo(); redraw(); });
  $("secondary").addEventListener("change", (event) => { secondary=event.target.value; ensureTwo(); redraw(); });
  $("category").addEventListener("change", renderList);
  $("search").addEventListener("input", renderList);
  $("missingOnly").addEventListener("change", renderList);
  let isComposing = false;
  function applyTranslation(text) {
    const entry = known.get(selectedKey);
    const validPlaceholders = same(placeholders(text), entry.placeholders);
    if (text.trim() && !validPlaceholders) {
      $("translationStatus").textContent = "格式參數不符合原文，尚未寫入：" + entry.placeholders.join(", ");
      $("translationStatus").className = "error";
      return;
    }
    if (text.trim()) packs[primary].translations[selectedKey] = text;
    else delete packs[primary].translations[selectedKey];
    $("translationStatus").className = "muted";
    const a = translate(primary, selectedKey), b = translate(secondary, selectedKey);
    $("translationStatus").textContent = a.fallback ? "主要語言缺漏：使用英文備援。" : "主要語言已提供翻譯。";
    $("previewPrimary").textContent = a.value;
    $("previewSecondary").textContent = b.value;
    // Preserve composition and caret position: never replace an active textarea on input.
    refreshLocales();
    renderList();
  }
  $("translation").addEventListener("compositionstart", () => { isComposing = true; });
  $("translation").addEventListener("compositionend", (e) => { isComposing = false; applyTranslation(e.target.value); });
  $("translation").addEventListener("input", (e) => { if (!isComposing) applyTranslation(e.target.value); });
  $("importButton").addEventListener("click", () => $("filePicker").click());
  $("filePicker").addEventListener("change", async (event) => {
    const file = event.target.files && event.target.files[0]; event.target.value = "";
    if (!file) return;
    if (file.size > 262144) { setMessage("拒絕超過 256 KiB 的語言包", "error"); return; }
    try {
      const loaded = validatePack(JSON.parse(await file.text()));
      packs[loaded.locale] = loaded;
      primary = loaded.locale;
      ensureTwo();
      redraw();
      setMessage("已載入 " + loaded.locale + "，共 " + completion(loaded.locale) + " 筆翻譯。只在本頁有效。", "ok");
    } catch (error) { setMessage("匯入失敗：" + error.message, "error"); }
  });
  $("newPackButton").addEventListener("click", () => {
    const name=$("newLocale").value.trim();
    if(!supportedLocale(name)){setMessage("請輸入語言標籤，例如 ko-KR", "error");return;}
    if(packs[name]){setMessage("語言包已存在，請使用匯入或編輯", "warning");return;}
    packs[name] = {format:"acx-language-pack-probe/1",locale:name,catalogRevision:catalog.revision,
      packVersion:"0.1.0",builtInSource:false,translations:Object.create(null)};
    primary=name;ensureTwo();redraw();setMessage("已建立空白 " + name + "。未翻譯的 200 項會使用英文備援。", "ok");
  });
  $("exportButton").addEventListener("click", () => {
    const pack = packs[primary];
    const clone = {format:pack.format,locale:pack.locale,catalogRevision:pack.catalogRevision,
      packVersion:pack.packVersion,translations:pack.translations};
    const json = JSON.stringify(clone,null,2) + "\n";
    const url=URL.createObjectURL(new Blob([json],{type:"application/json;charset=utf-8"}));
    const a=document.createElement("a");a.href=url;a.download="acx-lp0-" + primary + ".json";
    document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
    setMessage("已匯出 " + primary + "（" + completion(primary) + "/200）。", "ok");
  });
  for(const [key,label] of Object.entries(sections)){
    const o=document.createElement("option");o.value=key;o.textContent=label;$("category").append(o);
  }
  $("totalCount").textContent="· "+entries.length+" 筆";
  ensureTwo();redraw();
})();
