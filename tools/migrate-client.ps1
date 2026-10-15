# 26.2 客户端 API 迁移脚本（Screen 渲染与输入回调）
# 用法: powershell -ExecutionPolicy Bypass -File tools/migrate-client.ps1
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2\src\main\java"
$files = Get-ChildItem -Path $root -Recurse -Filter *.java
$enc = New-Object System.Text.UTF8Encoding $false
$n = 0

foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName)
    $o = $t

    # --- 渲染入口: render -> extractRenderState ---
    $t = [regex]::Replace($t, 'public void render\(GuiGraphicsExtractor', 'public void extractRenderState(GuiGraphicsExtractor')
    $t = $t.Replace('super.render(', 'super.extractRenderState(')
    $t = [regex]::Replace($t, 'protected void renderLabels\(GuiGraphicsExtractor', 'protected void extractLabels(GuiGraphicsExtractor')

    # --- 键盘: keyPressed(int,int,int) -> keyPressed(KeyEvent) ---
    $t = [regex]::Replace($t, 'public boolean keyPressed\(int (\w+), int (\w+), int (\w+)\)\s*\{',
        "public boolean keyPressed(KeyEvent event) {`r`n        int `$1 = event.key(); int `$2 = event.scancode(); int `$3 = event.modifiers();")
    $t = [regex]::Replace($t, 'public boolean keyReleased\(int (\w+), int (\w+), int (\w+)\)\s*\{',
        "public boolean keyReleased(KeyEvent event) {`r`n        int `$1 = event.key(); int `$2 = event.scancode(); int `$3 = event.modifiers();")

    # --- 鼠标: 参数重绑定，方法体保持原样 ---
    $t = [regex]::Replace($t, 'public boolean mouseClicked\(double (\w+), double (\w+), int (\w+)\)\s*\{',
        "public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {`r`n        double `$1 = event.x(); double `$2 = event.y(); int `$3 = event.button();")
    $t = [regex]::Replace($t, 'public boolean mouseReleased\(double (\w+), double (\w+), int (\w+)\)\s*\{',
        "public boolean mouseReleased(MouseButtonEvent event) {`r`n        double `$1 = event.x(); double `$2 = event.y(); int `$3 = event.button();")
    $t = [regex]::Replace($t, 'public boolean mouseDragged\(double (\w+), double (\w+), int (\w+), double (\w+), double (\w+)\)\s*\{',
        "public boolean mouseDragged(MouseButtonEvent event, double `$4, double `$5) {`r`n        double `$1 = event.x(); double `$2 = event.y(); int `$3 = event.button();")
    $t = [regex]::Replace($t, 'public boolean mouseScrolled\(double (\w+), double (\w+), double (\w+)\)\s*\{',
        "public boolean mouseScrolled(double `$1, double `$2, double __scrollX, double `$3) {")

    # --- 补齐 import ---
    if ($t -ne $o) {
        if ($t -match '\bKeyEvent\b' -and $t -notmatch 'import net\.minecraft\.client\.input\.KeyEvent;') {
            $t = [regex]::Replace($t, '(import net\.minecraft\.client\.gui\.GuiGraphicsExtractor;)', "`$1`r`nimport net.minecraft.client.input.KeyEvent;", 1)
        }
        if ($t -match '\bMouseButtonEvent\b' -and $t -notmatch 'import net\.minecraft\.client\.input\.MouseButtonEvent;') {
            $t = [regex]::Replace($t, '(import net\.minecraft\.client\.gui\.GuiGraphicsExtractor;)', "`$1`r`nimport net.minecraft.client.input.MouseButtonEvent;", 1)
        }
    }

    if ($t -ne $o) {
        [System.IO.File]::WriteAllText($f.FullName, $t, $enc)
        $n++
    }
}
Write-Output "migrate-client: modified $n files"
