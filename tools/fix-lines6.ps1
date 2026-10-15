# 第 6 批：TickEvent record 访问器 / ServerPlayer.server / Inventory.items / blit 8参 / getLongArray
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$log  = Join-Path $root "build_out.txt"
$enc  = New-Object System.Text.UTF8Encoding $false

# ---------- A. 全局 ----------
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # TickEvent.PlayerTickEvent.Post 的组件访问器（record 字段是 private）
    $t = [regex]::Replace($t, '\bevent\.player\b(?!\s*\()', 'event.player()')

    # ServerPlayer.server 私有
    $t = [regex]::Replace($t, '(\w+)\.server\.getPlayerList\(\)', '$1.level().getServer().getPlayerList()')

    # Inventory.items 私有
    $t = [regex]::Replace($t, 'for \(ItemStack (\w+) : (\w+)\.items\)', 'for (ItemStack $1 : $2.getNonEquipmentItems())')
    $t = [regex]::Replace($t, '(\w+)\.items(?![.\w(])', '$1.getNonEquipmentItems()')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines6(A): modified $n files"

# ---------- B. 行级：blit 8 参补贴图尺寸 ----------
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
        # 8 参数 blit：补纹理尺寸（GUI 贴图标准 256x256）
        $new = [regex]::Replace($new,
            '\.blit\((RenderPipelines\.\w+), ([^,()]+), ([^,()]+), ([^,()]+), ([^,()]+), ([^,()]+), ([^,()]+), ([^,()]+)\)',
            '.blit($1, $2, $3, $4, $5, $6, $7, $8, 256, 256)')
        # Optional 化 getter：int/long 数组
        $new = [regex]::Replace($new, '\.getLongArray\(([^()]+)\)', '.getLongArray($1).orElse(null)')
        $new = [regex]::Replace($new, '\.getIntArray\(([^()]+)\)', '.getIntArray($1).orElse(null)')
        if ($new -ne $orig) { $arr[$idx] = $new; $dirty = $true; $changed++ }
    }
    if ($dirty) { [System.IO.File]::WriteAllText($file, ($arr -join $sep), $enc) }
}
Write-Output "fix-lines6(B): changed $changed lines"
