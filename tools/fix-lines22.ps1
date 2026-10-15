# 第 22 批：NBT 列表/UUID / 传送签名 / 燃料 / 箭矢击退
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # 列表读取
    $t = [regex]::Replace($t, '(\w+)\.getList\((.+?),\s*Tag\.TAG_\w+\)', '$1.getListOrEmpty($2)')

    # NBT 的 UUID（26.2 移除了 putUUID/getUUID/hasUUID）
    $t = [regex]::Replace($t, '(\w+)\.putUUID\(([^,()]+),\s*UUID\.randomUUID\(\)\)', '$1.putString($2, UUID.randomUUID().toString())')
    $t = [regex]::Replace($t, '(\w+)\.hasUUID\(([^()]+)\)', '$1.contains($2)')
    $t = [regex]::Replace($t, 'UUID (\w+) = (\w+)\.getUUID\(([^()]+)\)', 'UUID $1 = UUID.fromString($2.getStringOr($3, ""))')

    # 传送（26.2 的 ServerPlayer.teleportTo 需要 Relative 集合与相机参数）
    $t = [regex]::Replace($t, '(\w+)\.teleportTo\((\w+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^)]+)\)',
        '$1.teleportTo($2, $3, $4, $5, java.util.Set.of(), $6, $7, true)')

    # 燃料时长（含带括号的第一个参数）
    $t = [regex]::Replace($t, 'ForgeHooks\.getBurnTime\((.+?),\s*(RecipeType\.\w+)\)', '$1.getBurnTime($2)')

    # 箭矢无击退 setter，移除该加成（力量加成仍保留）
    $t = [regex]::Replace($t, '^\s*if \(punchLevel > 0\) arrow\.setKnockback\(punchLevel\);\s*$', '', 'Multiline')
    $t = [regex]::Replace($t, 'int punchLevel = enchLevel\([^;]*\);\s*\r?\n', '')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines22: modified $n files"
