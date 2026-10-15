# 第 16 批：括号感知的 CHANNEL.send 补参 + 一批单点 API 修正
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0

function Add-SendTarget([string]$text) {
    $sb = New-Object System.Text.StringBuilder
    $i = 0
    $len = $text.Length
    while ($i -lt $len) {
        $idx = $text.IndexOf('CHANNEL.send(', $i)
        if ($idx -lt 0) { [void]$sb.Append($text.Substring($i)); break }
        $open = $idx + 'CHANNEL.send'.Length      # 指向 '('
        [void]$sb.Append($text.Substring($i, $open - $i + 1))
        # 扫描参数区，统计顶层逗号并找到匹配的 ')'
        $depth = 1; $j = $open + 1; $topCommas = 0
        $inStr = $false; $charStr = $false
        while ($j -lt $len -and $depth -gt 0) {
            $ch = $text[$j]
            if ($inStr) {
                if ($ch -eq '\') { $j += 2; continue }
                if ($ch -eq '"') { $inStr = $false }
            } elseif ($charStr) {
                if ($ch -eq '\') { $j += 2; continue }
                if ($ch -eq "'") { $charStr = $false }
            } else {
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
            if ($topCommas -eq 0) {
                [void]$sb.Append($inner.TrimEnd())
                [void]$sb.Append(', PacketDistributor.SERVER.noArg())')
            } else {
                [void]$sb.Append($inner)
                [void]$sb.Append(')')
            }
            $i = $j + 1
        } else {
            [void]$sb.Append($text.Substring($open + 1)); break
        }
    }
    return $sb.ToString()
}

foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    if ($t.Contains('CHANNEL.send(')) { $t = Add-SendTarget $t }

    $t = $t.Replace('com.mojang.blaze3d.vertex.Matrix3x2fStack', 'org.joml.Matrix3x2fStack')
    $t = $t.Replace('protected void extractBackground(GuiGraphicsExtractor', 'public void extractBackground(GuiGraphicsExtractor')
    $t = $t.Replace('.withInitialValue(', '.withValues(')
    $t = $t.Replace('TagParser.parseTag(', 'TagParser.parseCompoundFully(')
    $t = [regex]::Replace($t, '(\w+)\.getInt\("(\w+)"\)', '$1.getIntOr("$2", 0)')
    $t = [regex]::Replace($t, '(\w+)\.getString\("(\w+)"\)(?!\s*,)', '$1.getStringOr("$2", "")')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines16: modified $n files"
