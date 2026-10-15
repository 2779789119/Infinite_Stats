# 第 13 批：GUI 残留 / 属性操作枚举 / 姿态栈 / 杂项 API
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

    # 删除 26.2 已移除的 RenderSystem 调用
    for ($i = $arr.Count - 1; $i -ge 0; $i--) {
        if ($arr[$i] -match 'RenderSystem\.(depthMask|setShaderTexture|colorMask|setShaderColor|setShader)\s*\(') {
            $arr.RemoveAt($i); $dirty = $true
        }
    }

    $t = ($arr -join $sep)

    # ContainerScreen 的标签/提示入口
    $t = $t.Replace('super.renderLabels(', 'super.extractLabels(')
    $t = $t.Replace('this.setTooltipForNextFrame(', 'this.extractTooltip(')

    # 姿态栈：26.2 只有 2D（x, y）
    $t = [regex]::Replace($t, '\.pose\(\)\.translate\(([^,()]+),\s*([^,()]+),\s*[^,()]+\)', '.pose().translate($1, $2)')
    $t = [regex]::Replace($t, '\.pose\(\)\.scale\(([^,()]+),\s*([^,()]+),\s*[^,()]+\)', '.pose().scale($1, $2)')

    # PoseStack -> Matrix3x2fStack
    $t = $t.Replace('PoseStack', 'com.mojang.blaze3d.vertex.Matrix3x2fStack')

    # 属性操作枚举改名
    $t = $t.Replace('AttributeModifier.Operation.ADDITION', 'AttributeModifier.Operation.ADD_VALUE')
    $t = $t.Replace('AttributeModifier.Operation.MULTIPLY_BASE', 'AttributeModifier.Operation.ADD_MULTIPLIED_BASE')
    $t = $t.Replace('AttributeModifier.Operation.MULTIPLY_TOTAL', 'AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL')
    $t = [regex]::Replace($t, '(?<![\w.])ADDITION\b(?!\s*\))', 'ADD_VALUE')

    # 注册表查找
    $t = $t.Replace('.registryOrThrow(', '.lookupOrThrow(')

    # 物品使用时长需要实体参数
    $t = [regex]::Replace($t, '(\w+)\.getUseDuration\(\)', '$1.getUseDuration(player)')

    # Ingredient#getItems -> items()
    $t = [regex]::Replace($t, 'ItemStack\[\] (\w+) = (\w+)\.getItems\(\);',
        'java.util.List<ItemStack> $1 = $2.items().map(net.minecraft.world.item.ItemStack::new).toList();')

    # hasImpulse 已不可写
    $t = [regex]::Replace($t, '^\s*\w+\.hasImpulse = true;\s*$', '', 'Multiline')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines13: modified $n files"
