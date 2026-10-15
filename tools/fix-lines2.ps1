# 行级精确修复 第 2 批：Identifier / 网络发送 / 背包 / blit / 字段访问
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$log  = Join-Path $root "build_out.txt"

$lines = Get-Content -Encoding UTF8 $log
$targets = @{}
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^(D:\\mod\\infinitestats-forge-26_2\\src\\[^:]+\.java):(\d+): error:') {
        $file = $matches[1]; $ln = [int]$matches[2]
        $src = if ($i + 1 -lt $lines.Count) { $lines[$i + 1] } else { "" }
        if (-not $targets.ContainsKey($file)) { $targets[$file] = @{} }
        $targets[$file][$ln] = $src
    }
}

$enc = New-Object System.Text.UTF8Encoding $false
$changed = 0

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

        # ---- Identifier 构造 ----
        $new = [regex]::Replace($new, 'new Identifier\(\s*([^,()]+?)\s*,\s*([^()]+?)\s*\)', 'Identifier.fromNamespaceAndPath($1, $2)')
        $new = [regex]::Replace($new, 'new Identifier\("([^"]*)"\)', 'Identifier.withDefaultNamespace("$1")')
        $new = [regex]::Replace($new, 'new Identifier\(([^()]+)\)', 'Identifier.parse($1)')

        # ---- PacketDistributor.with(() -> x) -> with(x) ----
        $new = [regex]::Replace($new, '\.with\(\(\)\s*->\s*([\w\.\(\)]+)\)', '.with($1)')

        # ---- Inventory 私有字段 ----
        $new = [regex]::Replace($new, '\.items\.size\(\)', '.getContainerSize()')
        $new = [regex]::Replace($new, '\.items\.get\(', '.getItem(')
        $new = [regex]::Replace($new, '\.items\.set\(', '.setItem(')
        $new = [regex]::Replace($new, '\.selected\b', '.getSelectedSlot()')

        # ---- Level 私有字段 ----
        $new = [regex]::Replace($new, '\.isClientSide\b(?!\s*\()', '.isClientSide()')
        $new = [regex]::Replace($new, '\.random\b(?!\s*\()', '.getRandom()')

        # ---- blit 参数重排: (tex,x,y,w,h,u,v,sw,sh,tw,th) -> (pipeline,tex,x,y,u,v,w,h,sw,sh,tw,th) ----
        $m = [regex]::Match($new, '\.blit\((\w+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^)]+)\)')
        if ($m.Success) {
            $new = $new.Substring(0, $m.Index) + ".blit(RenderPipelines.GUI_TEXTURED, " + $m.Groups[1].Value + ", " + $m.Groups[2].Value + ", " + $m.Groups[3].Value + ", " +
                   $m.Groups[6].Value + ", " + $m.Groups[7].Value + ", " + $m.Groups[4].Value + ", " + $m.Groups[5].Value + ", " +
                   $m.Groups[8].Value + ", " + $m.Groups[9].Value + ", " + $m.Groups[10].Value + ", " + $m.Groups[11].Value + ")" +
                   $new.Substring($m.Index + $m.Length)
        }

        if ($new -ne $orig) {
            $arr[$idx] = $new
            $dirty = $true
            $changed++
        }
    }

    if ($dirty) { [System.IO.File]::WriteAllText($file, ($arr -join $sep), $enc) }
}
Write-Output "fix-lines2: changed $changed lines"
