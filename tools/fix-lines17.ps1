# 第 17 批：Widget 子类渲染方法名 / 配方 holder 循环 / 消息发送 / 权限 / Optional getter
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false

function Strip-SendSystemMessageOverlay([string]$text) {
    # sendSystemMessage(msg, true/false) -> sendSystemMessage(msg)
    $sb = New-Object System.Text.StringBuilder
    $i = 0; $len = $text.Length
    while ($i -lt $len) {
        $idx = $text.IndexOf('.sendSystemMessage(', $i)
        if ($idx -lt 0) { [void]$sb.Append($text.Substring($i)); break }
        $open = $idx + '.sendSystemMessage'.Length
        [void]$sb.Append($text.Substring($i, $open - $i + 1))
        $depth = 1; $j = $open + 1; $commas = @()
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
                elseif ($ch -eq ',' -and $depth -eq 1) { $commas += $j }
            }
            $j++
        }
        if ($depth -eq 0 -and $commas.Count -eq 1) {
            $second = $text.Substring($commas[0] + 1, $j - $commas[0] - 1).Trim()
            if ($second -eq 'true' -or $second -eq 'false') {
                [void]$sb.Append($text.Substring($open + 1, $commas[0] - $open - 1).TrimEnd())
                [void]$sb.Append(')')
            } else {
                [void]$sb.Append($text.Substring($open + 1, $j - $open - 1)); [void]$sb.Append(')')
            }
        } elseif ($depth -eq 0) {
            [void]$sb.Append($text.Substring($open + 1, $j - $open - 1)); [void]$sb.Append(')')
        } else { [void]$sb.Append($text.Substring($open + 1)); break }
        $i = $j + 1
    }
    return $sb.ToString()
}

$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # EmcScreen 的 AbstractWidget 子类要用 extractWidgetRenderState
    if ($f.Name -eq 'EmcScreen.java') {
        $t = $t.Replace('protected void extractContents(GuiGraphicsExtractor', 'protected void extractWidgetRenderState(GuiGraphicsExtractor')
    }

    # 配方管理器返回 RecipeHolder
    $t = [regex]::Replace($t, 'for \(Recipe<\?> (\w+) : (\w+)\.getRecipes\(\)\) \{',
        "for (RecipeHolder<?> recipeHolder : `$2.getRecipes()) {`r`n            Recipe<?> `$1 = recipeHolder.value();")

    # 消息发送：26.2 的 Player 没有 overlay 重载
    if ($t.Contains('.sendSystemMessage(')) { $t = Strip-SendSystemMessageOverlay $t }

    # 权限
    $t = [regex]::Replace($t, '(\w+)\.hasPermission\(net\.minecraft\.server\.permissions\.Permissions\.(\w+)\)',
        '$1.permissions().hasPermission(net.minecraft.server.permissions.Permissions.$2)')

    # Optional getter 漏网
    $t = [regex]::Replace($t, '(\w+)\.getDouble\("(\w+)"\)', '$1.getDoubleOr("$2", 0.0)')

    # ClickType 残留
    $t = [regex]::Replace($t, '(?<![\w.])ClickType\b', 'ContainerInput')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines17: modified $n files"
