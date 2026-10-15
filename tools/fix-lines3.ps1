# 行级精确修复 第 3 批：super 调用 / blit 7参 / contains / getListOrEmpty / Registry.getValue / sendSystemMessage
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

        # super 输入回调：新签名只接受事件对象（保留原拖拽增量参数名）
        $new = [regex]::Replace($new, 'super\.keyPressed\([^)]*\)',   'super.keyPressed(event)')
        $new = [regex]::Replace($new, 'super\.keyReleased\([^)]*\)',  'super.keyReleased(event)')
        $new = [regex]::Replace($new, 'super\.mouseClicked\([^)]*\)', 'super.mouseClicked(event, doubleClick)')
        $new = [regex]::Replace($new, 'super\.mouseReleased\([^)]*\)','super.mouseReleased(event)')
        $new = [regex]::Replace($new, 'super\.mouseDragged\([^,]+,\s*[^,]+,\s*[^,]+,\s*(\w+),\s*(\w+)\)', 'super.mouseDragged(event, $1, $2)')
        $new = [regex]::Replace($new, 'super\.mouseDragged\([^)]*\)', 'super.mouseDragged(event, 0.0, 0.0)')
        $new = [regex]::Replace($new, 'super\.mouseScrolled\((\w+),\s*(\w+),\s*(\w+)\)', 'super.mouseScrolled($1, $2, 0.0, $3)')
        $new = [regex]::Replace($new, 'super\.mouseScrolled\([^)]*\)','super.mouseScrolled(0.0, 0.0, 0.0, 0.0)')

        # blit 7 参数版本：补 RenderPipeline 前缀
        $m = [regex]::Match($new, '\.blit\((\w+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^)]+)\)')
        if ($m.Success -and $new -notmatch 'RenderPipelines') {
            $new = $new.Substring(0, $m.Index) + ".blit(RenderPipelines.GUI_TEXTURED, " + ($m.Groups[1..7].Value -join ", ") + ")" + $new.Substring($m.Index + $m.Length)
        }

        # contains / getListOrEmpty 去掉类型参数
        $new = [regex]::Replace($new, '\.contains\(([^,()]+),\s*[A-Za-z0-9_\.]+\)', '.contains($1)')
        $new = [regex]::Replace($new, '\.getListOrEmpty\(([^,()]+),\s*[A-Za-z0-9_\.]+\)', '.getListOrEmpty($1)')

        # Registry: get(Identifier) -> getValue(Identifier)
        $new = [regex]::Replace($new, 'BuiltInRegistries\.(\w+)\.get\(', 'BuiltInRegistries.$1.getValue(')

        # Player.sendSystemMessage(Component) 无 overlay 重载
        $new = [regex]::Replace($new, '\.sendSystemMessage\((.*?),\s*(?:true|false)\)', '.sendSystemMessage($1)')

        if ($new -ne $orig) { $arr[$idx] = $new; $dirty = $true; $changed++ }
    }

    if ($dirty) { [System.IO.File]::WriteAllText($file, ($arr -join $sep), $enc) }
}
Write-Output "fix-lines3: changed $changed lines"
