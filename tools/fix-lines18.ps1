# 第 18 批：物品 NBT/冷却/步高/附魔描述/配方管理器 等单点适配
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # ItemStack.setTag -> CustomData 组件
    $t = [regex]::Replace($t, '(\w+)\.setTag\(([^;()]+(?:\(\))?)\)',
        'net.minecraft.world.item.component.CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, $1, $2)')

    # 列表长度
    $t = [regex]::Replace($t, '(\w+)\.length == 0', '$1.size() == 0')
    $t = [regex]::Replace($t, '(\w+)\.length > 0', '$1.size() > 0')

    # 配方管理器
    $t = $t.Replace('.getRecipeManager()', '.recipeAccess()')

    # 附魔描述（26.2 是 record 组件）
    $t = [regex]::Replace($t, '(\w+)\.value\(\)\.getDescriptionId\(\)', '$1.value().description().getString()')
    if ($f.Name -eq 'ItemEditorScreen.java') {
        $t = [regex]::Replace($t, '(\w+)\.getDescriptionId\(\)', '$1.description().getString()')
    }

    # 步高改用 STEP_HEIGHT 属性
    $t = [regex]::Replace($t, '(\w+)\.setMaxUpStep\(([^)]+)\)',
        '$1.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue($2)')

    # 冷却清除：26.2 只接受 cooldown group（默认即物品注册名）
    $t = [regex]::Replace($t, '(\w+)\.removeCooldown\((\w+)\.getItem\(\)\)',
        '$1.removeCooldown(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey($2.getItem()))')

    # 弩蓄力需要使用者
    $t = [regex]::Replace($t, 'CrossbowItem\.getChargeDuration\((\w+)\)', 'CrossbowItem.getChargeDuration($1, player)')

    # 箭矢创建多一个「发射武器」参数
    $t = [regex]::Replace($t, '\.createArrow\((\w+), ([^,]+), (\w+)\)', '.createArrow($1, $2, $3, null)')

    # ServerStartedEvent 的访问器
    $t = $t.Replace('event.level().getServer()', 'event.getServer()')

    # offhand 语法修正（之前替换产生了多余括号）
    $t = [regex]::Replace($t, 'for \(ItemStack (\w+) : java\.util\.List\.of\((\w+)\.getItem\(Inventory\.SLOT_OFFHAND\)\)\) (\w+)\((\w+), \1\);',
                              'learn($4, $2.getItem(Inventory.SLOT_OFFHAND));')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines18: modified $n files"
