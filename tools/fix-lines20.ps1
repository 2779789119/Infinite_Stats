# 第 20 批：隐藏 GUI 判断 / 残留 sendToServer / 燃料 / 注册表取值 / NBT 写入
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false

function Convert-SendToServer([string]$text) {
    $sb = New-Object System.Text.StringBuilder
    $i = 0; $len = $text.Length
    while ($i -lt $len) {
        $idx = $text.IndexOf('.sendToServer(', $i)
        if ($idx -lt 0) { [void]$sb.Append($text.Substring($i)); break }
        $open = $idx + '.sendToServer'.Length
        [void]$sb.Append($text.Substring($i, $open - $i + 1))
        $depth = 1; $j = $open + 1; $topCommas = 0
        $inStr = $false; $charStr = $false
        while ($j -lt $len -and $depth -gt 0) {
            $ch = $text[$j]
            if ($inStr) { if ($ch -eq '\') { $j += 2; continue }; if ($ch -eq '"') { $inStr = $false } }
            elseif ($charStr) { if ($ch -eq '\') { $j += 2; continue }; if ($ch -eq "'") { $charStr = $false } }
            else {
                if ($ch -eq '"') { $inStr = $true }
                elseif ($ch -eq "'") { $charStr = $true }
                elseif ($ch -eq '(') { $depth++ }
                elseif ($ch -eq ')') { $depth--; if ($depth -eq 0) { break } }
                elseif ($ch -eq ',' -and $depth -eq 1) { $topCommas++ }
            }
            $j++
        }
        if ($depth -eq 0) {
            $inner = $text.Substring($open + 1, $j - $open - 1)
            [void]$sb.Append($inner.TrimEnd())
            if ($topCommas -eq 0) { [void]$sb.Append(', PacketDistributor.SERVER.noArg()') }
            [void]$sb.Append(')')
            $i = $j + 1
        } else { [void]$sb.Append($text.Substring($open + 1)); break }
    }
    return ($sb.ToString()).Replace('.sendToServer(', '.send(')
}

$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    if ($t.Contains('.sendToServer(')) { $t = Convert-SendToServer $t }

    # 26.2 的 Options 没有 hideGui 字段，HUD 显隐由本模组设置控制
    $t = $t.Replace('mc.options.hideGui', 'false')
    $t = $t.Replace('minecraft.options.hideGui', 'false')

    # 燃料时长
    $t = [regex]::Replace($t, 'ForgeHooks\.getBurnTime\(([^,()]+),\s*([^()]+)\)', '$1.getBurnTime($2)')

    # 注册表按 id 取值
    $t = [regex]::Replace($t, 'BuiltInRegistries\.(\w+)\.getHolder\(ResourceKey\.create\(Registries\.\1,\s*(\w+)\)\)',
        'BuiltInRegistries.$1.get($2)')

    # NBT 写入：26.2 统一为 put(name, tag)
    $t = [regex]::Replace($t, '(\w+)\.addTagElement\(([^,()]+),\s*([^()]+)\)', '$1.put($2, $3)')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines20: modified $n files"
