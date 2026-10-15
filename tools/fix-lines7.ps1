# 第 7 批：Gui.screen() / extractBackground / ItemStack NBT 组件化 / recipeAccess / 燃料 / Ingredient.items()
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # SimpleChannel 的正确包路径
    $t = $t.Replace('import net.minecraftforge.network.simple.SimpleChannel;', 'import net.minecraftforge.network.SimpleChannel;')

    # Gui 的 screen 访问器
    $t = [regex]::Replace($t, '\.gui\.getScreen\(\)', '.gui.screen()')

    # renderBackground(X) -> extractBackground(X, 0, 0, 0)
    $t = [regex]::Replace($t, '(?<!\.)renderBackground\((\w+)\);', 'extractBackground($1, 0, 0, 0);')

    # ItemStack NBT -> 数据组件
    $t = [regex]::Replace($t, '(\w+)\.getTag\(\)',
        '$1.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag()')
    $t = [regex]::Replace($t, '(\w+)\.setTag\((\w+)\)',
        'net.minecraft.world.item.component.CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, $1, $2)')

    # CompoundTag 的 tag element 访问
    $t = [regex]::Replace($t, '(\w+)\.getOrCreateTagElement\(([^()]+)\)', '$1.getCompoundOrEmpty($2)')
    $t = [regex]::Replace($t, '(\w+)\.addTagElement\(([^,()]+),\s*([^()]+)\)', '$1.put($2, $3)')

    # 配方管理器
    $t = [regex]::Replace($t, '(\w+)\.getRecipeManager\(\)', '$1.recipeAccess()')

    # 燃料
    $t = [regex]::Replace($t, 'ForgeHooks\.getBurnTime\(([^,()]+),\s*([^()]+)\)', '$1.getBurnTime($2)')

    # Ingredient#getItems -> items()
    $t = [regex]::Replace($t, 'for \(ItemStack (\w+) : (\w+)\.getItems\(\)\)',
        'for (ItemStack $1 : $2.items().map(net.minecraft.world.item.ItemStack::new).toList())')

    # Shift/Ctrl 修饰键（Minecraft 提供）
    $t = [regex]::Replace($t, '(?<![\w.])hasShiftDown\(\)', 'net.minecraft.client.Minecraft.getInstance().hasShiftDown()')
    $t = [regex]::Replace($t, '(?<![\w.])hasControlDown\(\)', 'net.minecraft.client.Minecraft.getInstance().hasControlDown()')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines7: modified $n files"
