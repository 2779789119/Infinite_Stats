# 第 4 批：客户端渲染方法名、RenderSystem 清理、Widget 回调、ResourceKey.identifier()
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$log  = Join-Path $root "build_out.txt"
$srcRoot = Join-Path $root "src\main\java"

# ---------- A. 全局结构性改名 ----------
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # ContainerScreen: renderBg(g, partial, mx, my) -> extractBackground(g, mx, my, partial)
    $t = [regex]::Replace($t, 'protected void renderBg\(GuiGraphicsExtractor (\w+), float (\w+), int (\w+), int (\w+)\)',
                              'protected void extractBackground(GuiGraphicsExtractor $1, int $3, int $4, float $2)')

    # Widget: renderWidget -> extractWidgetRenderState
    $t = [regex]::Replace($t, 'protected void renderWidget\(GuiGraphicsExtractor', 'protected void extractWidgetRenderState(GuiGraphicsExtractor')

    # Widget: onClick(double,double) -> onClick(MouseButtonEvent, boolean)
    $t = [regex]::Replace($t, 'public void onClick\(double (\w+), double (\w+)\)\s*\{',
        "public void onClick(MouseButtonEvent event, boolean doubleClick) {`r`n            double `$1 = event.x(); double `$2 = event.y();")

    # ResourceKey#location() -> identifier()
    $t = [regex]::Replace($t, '\.location\(\)', '.identifier()')

    # 单参 extractBackground 调用补齐参数
    $t = [regex]::Replace($t, 'this\.extractBackground\((\w+)\);', 'this.extractBackground($1, 0, 0, 0);')

    if ($t -ne $o) {
        # 需要 MouseButtonEvent 时补 import
        if ($t -match '\bMouseButtonEvent\b') {
            $t = [regex]::Replace($t, '(import net\.minecraft\.client\.gui\.GuiGraphicsExtractor;)',
                "`$1`r`nimport net.minecraft.client.input.MouseButtonEvent;", 1)
        }
        if ($t -match 'RenderPipelines\.' -and $t -notmatch 'import net\.minecraft\.client\.renderer\.RenderPipelines;') {
            $t = [regex]::Replace($t, '(import net\.minecraft\.client\.gui\.GuiGraphicsExtractor;)',
                "`$1`r`nimport net.minecraft.client.renderer.RenderPipelines;", 1)
        }
        [System.IO.File]::WriteAllText($f.FullName, $t, $enc)
        $n++
    }
}
Write-Output "fix-lines4(A): modified $n files"

# ---------- B. 删除已不存在的 RenderSystem 调用行 ----------
$lines = Get-Content -Encoding UTF8 $log
$targets = @{}
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^(D:\\mod\\infinitestats-forge-26_2\\src\\[^:]+\.java):(\d+): error:') {
        $file = $matches[1]; $ln = [int]$matches[2]
        if (-not $targets.ContainsKey($file)) { $targets[$file] = @() }
        if (-not ($targets[$file] -contains $ln)) { $targets[$file] += $ln }
    }
}

$removed = 0
foreach ($file in $targets.Keys) {
    $content = [System.IO.File]::ReadAllText($file)
    $sep = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
    $arr = New-Object System.Collections.Generic.List[string]
    $arr.AddRange($content -split "`r?`n")
    # 从后往前删，避免行号漂移
    foreach ($ln in ($targets[$file] | Sort-Object -Descending)) {
        $idx = $ln - 1
        if ($idx -lt 0 -or $idx -ge $arr.Count) { continue }
        $s = $arr[$idx]
        if ($s -match 'RenderSystem\.(setShader|setShaderColor|setShaderTexture|colorMask)\s*\(') {
            $arr.RemoveAt($idx)
            $removed++
        }
    }
    [System.IO.File]::WriteAllText($file, ($arr -join $sep), $enc)
}
Write-Output "fix-lines4(B): removed $removed RenderSystem lines"
