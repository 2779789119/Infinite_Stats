# 第 9 批：ModList / Widget.extractRenderState / Component.Serializer / AttributeModifier / Advancement
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$log  = Join-Path $root "build_out.txt"
$enc  = New-Object System.Text.UTF8Encoding $false

# ---------- A. 全局 ----------
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # ModList：26.2 全部为静态方法
    $t = $t.Replace('ModList.get().isLoaded(', 'ModList.isLoaded(')

    # Widget 渲染入口
    $t = [regex]::Replace($t, '\.render\((graphics|g|gfx|guiGraphics|graphics2)(,\s*[^;]+)\);', '.extractRenderState($1$2;')

    # Component.Serializer -> TextJson
    if ($t -match 'Component\.Serializer\.') {
        $t = $t.Replace('Component.Serializer.fromJson(', 'com.infinitestats.util.TextJson.fromJson(')
        $t = $t.Replace('Component.Serializer.toJson(', 'com.infinitestats.util.TextJson.toJson(')
    }

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines9(A): modified $n files"

# ---------- B. 行级 ----------
$lines = Get-Content -Encoding UTF8 $log
$targets = @{}
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^(D:\\mod\\infinitestats-forge-26_2\\src\\[^:]+\.java):(\d+): error:') {
        $file = $matches[1]; $ln = [int]$matches[2]
        if (-not $targets.ContainsKey($file)) { $targets[$file] = @() }
        if (-not ($targets[$file] -contains $ln)) { $targets[$file] += $ln }
    }
}
$changed = 0
foreach ($file in $targets.Keys) {
    $content = [System.IO.File]::ReadAllText($file)
    $sep = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
    $arr = $content -split "`r?`n"
    $dirty = $false
    foreach ($ln in $targets[$file]) {
        $idx = $ln - 1
        if ($idx -lt 0 -or $idx -ge $arr.Count) { continue }
        $orig = $arr[$idx]; $new = $orig
        # AttributeModifier 是 record，访问器没有 get 前缀
        if ($new -match '\.getAmount\(\)' -and $new -match '(AttributeModifier|m\.|existing\.)') {
            $new = $new.Replace('.getAmount()', '.amount()')
        }
        $new = $new.Replace('.getOperation()', '.operation()')
        # Advancement：getAdvancement -> get（返回 AdvancementHolder）
        $new = [regex]::Replace($new, '\.getAdvancements\(\)\.getAdvancement\(', '.getAdvancements().get(')
        if ($new -ne $orig) { $arr[$idx] = $new; $dirty = $true; $changed++ }
    }
    if ($dirty) { [System.IO.File]::WriteAllText($file, ($arr -join $sep), $enc) }
}
Write-Output "fix-lines9(B): changed $changed lines"
