Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = Join-Path $env:USERPROFILE ".gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.15_mapped_official_1.20.1\forge-1.20.1-47.4.15_mapped_official_1.20.1-sources.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($jar)
# find any entry with Workbench (any case) across whole jar
$z.Entries | Where-Object { $_.FullName -like '*orkbench*' } | ForEach-Object { Write-Host ("WB: " + $_.FullName) }
# also list all menu classes in inventory
$z.Entries | Where-Object { $_.FullName -like 'net/minecraft/world/inventory/*Menu*.java' } | ForEach-Object { Write-Host ("MENU: " + $_.FullName) }
# read MenuType.java content
$mt = $z.Entries | Where-Object { $_.FullName -eq 'net/minecraft/world/inventory/MenuType.java' }
if ($mt) {
    $sr = New-Object System.IO.StreamReader($mt.Open())
    $txt = $sr.ReadToEnd(); $sr.Close()
    # print lines containing 'public static final' and 'MenuType'
    $txt -split "`n" | Where-Object { $_ -match 'public static final MenuType|CRAFTING|WORKBENCH|INVENTORY' } | ForEach-Object { Write-Host $_ }
}
$z.Dispose()
