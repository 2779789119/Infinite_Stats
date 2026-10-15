# 第 15 批：Matrix3x2fStack import / RenderSystem 残留 / CompoundTag Optional getter / 渲染提示
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $content = [System.IO.File]::ReadAllText($f.FullName)
    $o = $content
    $sep = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
    $arr = New-Object System.Collections.Generic.List[string]
    $arr.AddRange($content -split "`r?`n")
    $dirty = $false

    # 删除 26.2 已移除的 RenderSystem 深度调用
    for ($i = $arr.Count - 1; $i -ge 0; $i--) {
        if ($arr[$i] -match 'RenderSystem\.(disableDepthTest|enableDepthTest|depthMask|clear)\s*\(') {
            $arr.RemoveAt($i); $dirty = $true
        }
    }
    $t = ($arr -join $sep)

    # 修正被前缀破坏的 import
    $t = $t.Replace('com.mojang.blaze3d.vertex.com.mojang.blaze3d.vertex.Matrix3x2fStack', 'com.mojang.blaze3d.vertex.Matrix3x2fStack')
    $t = $t.Replace('import com.mojang.blaze3d.vertex.Matrix3x2fStack;', 'import com.mojang.blaze3d.vertex.Matrix3x2fStack;')

    # 提示框入口
    $t = [regex]::Replace($t, 'renderTooltip\(', 'extractTooltip(')
    $t = $t.Replace('Screen.hasControlDown()', 'net.minecraft.client.Minecraft.getInstance().hasControlDown()')

    # CompoundTag Optional getter
    $t = [regex]::Replace($t, '(\w+)\.getDouble\((?!\s*")([^,()]+)\)', '$1.getDoubleOr($2, 0.0)')
    $t = [regex]::Replace($t, '(\w+)\.getInt\((?!\s*")([^,()]+)\)', '$1.getIntOr($2, 0)')
    $t = [regex]::Replace($t, '(\w+)\.getShort\((?!\s*")([^,()]+)\)', '$1.getShortOr($2, (short) 0)')
    $t = [regex]::Replace($t, '(\w+)\.getFloat\((?!\s*")([^,()]+)\)', '$1.getFloatOr($2, 0.0f)')
    $t = [regex]::Replace($t, '(\w+)\.getBoolean\((?!\s*")([^,()]+)\)', '$1.getBooleanOr($2, false)')
    $t = $t.Replace('.getAllKeys()', '.keySet()')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines15: modified $n files"
