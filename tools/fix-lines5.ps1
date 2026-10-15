# 第 5 批：ClickType / Inventory 槽位 / Minecraft.gui / AbstractArrow / 配方输入 / 文本序列化
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # ClickType -> ContainerInput
    $t = $t.Replace('net.minecraft.world.inventory.ClickType', 'net.minecraft.world.inventory.ContainerInput')
    $t = [regex]::Replace($t, '\bClickType\.', 'ContainerInput.')

    # Inventory 私有字段
    $t = [regex]::Replace($t, 'for \(ItemStack (\w+) : (\w+)\.offhand\)', 'for (ItemStack $1 : java.util.List.of($2.getItem(Inventory.SLOT_OFFHAND)))')
    $t = [regex]::Replace($t, '(\w+)\.offhand(?![.\w])', "$1.getItem(Inventory.SLOT_OFFHAND)")
    $t = [regex]::Replace($t, 'for \(ItemStack (\w+) : (\w+)\.armor\)',
        'for (ItemStack $1 : java.util.List.of($2.getItem(36), $2.getItem(37), $2.getItem(38), $2.getItem(39)))')
    $t = [regex]::Replace($t, '(\w+)\.armor(?![.\w])', "$1.getNonEquipmentItems()")

    # ServerPlayer.getServer() -> level().getServer()
    $t = [regex]::Replace($t, '(\w+)\.getServer\(\)', '$1.level().getServer()')
    $t = $t.Replace('.level().getServer().level()', '.level()')

    # Minecraft: screen/setScreen -> gui.*
    $t = [regex]::Replace($t, '\b(\w+)\.screen\b(?!\s*\()', '$1.gui.getScreen()')
    $t = [regex]::Replace($t, '\b(\w+)\.setScreen\(', '$1.gui.setScreen(')

    # AbstractArrow 包路径
    $t = $t.Replace('import net.minecraft.world.entity.projectile.AbstractArrow;',
                    'import net.minecraft.world.entity.projectile.arrow.AbstractArrow;')

    # Recipe#getIngredients -> placementInfo().ingredients()
    $t = [regex]::Replace($t, '(\w+)\.getIngredients\(\)', '$1.placementInfo().ingredients()')

    # 物品 display NBT -> 组件
    $t = [regex]::Replace($t, '(\w+)\.removeTagKey\("display"\)', '$1.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME)')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines5: modified $n files"
