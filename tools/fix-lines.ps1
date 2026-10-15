# 行级精确修复：解析 javac 错误位置，仅修改报错行
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$log  = Join-Path $root "build_out.txt"

$lines = Get-Content $log
# 收集 (file, lineNo) -> 该行源码
$targets = @{}
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^(D:\\mod\\infinitestats-forge-26_2\\src\\[^:]+\.java):(\d+): error:') {
        $file = $matches[1]; $ln = [int]$matches[2]
        $src = if ($i + 1 -lt $lines.Count) { $lines[$i + 1] } else { "" }
        if (-not $targets.ContainsKey($file)) { $targets[$file] = @{} }
        $targets[$file][$ln] = $src
    }
}
Write-Output ("files with errors: " + $targets.Count)

$enc = New-Object System.Text.UTF8Encoding $false
$changedLines = 0

foreach ($file in $targets.Keys) {
    $content = [System.IO.File]::ReadAllText($file)
    $sep = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
    $arr = $content -split "`r?`n"
    $dirty = $false

    foreach ($ln in $targets[$file].Keys) {
        $idx = $ln - 1
        if ($idx -lt 0 -or $idx -ge $arr.Count) { continue }
        $orig = $arr[$idx]
        $new  = $orig

        # ---- CompoundTag: 1.21+ 的 Optional 化 getter ----
        $new = [regex]::Replace($new, '\.contains\(([^,()]+),\s*\d+\)', '.contains($1)')
        $new = [regex]::Replace($new, '\.getCompound\(([^()]+)\)', '.getCompoundOrEmpty($1)')
        $new = [regex]::Replace($new, '\.getList\(([^,()]+),\s*\d+\)', '.getListOrEmpty($1)')
        $new = [regex]::Replace($new, '\.getList\(([^()]+)\)', '.getListOrEmpty($1)')
        $new = [regex]::Replace($new, '\.getString\(([^()]+)\)', '.getStringOr($1, "")')
        $new = [regex]::Replace($new, '\.getLong\(([^()]+)\)', '.getLongOr($1, 0L)')
        $new = [regex]::Replace($new, '\.getInt\(([^()]+)\)', '.getIntOr($1, 0)')
        $new = [regex]::Replace($new, '\.getBoolean\(([^()]+)\)', '.getBooleanOr($1, false)')
        $new = [regex]::Replace($new, '\.getDouble\(([^()]+)\)', '.getDoubleOr($1, 0.0)')
        $new = [regex]::Replace($new, '\.getFloat\(([^()]+)\)', '.getFloatOr($1, 0.0f)')

        if ($new -ne $orig) {
            $arr[$idx] = $new
            $dirty = $true
            $changedLines++
        }
    }

    if ($dirty) {
        [System.IO.File]::WriteAllText($file, ($arr -join $sep), $enc)
    }
}
Write-Output "fix-lines: changed $changedLines lines"
