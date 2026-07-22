Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = Join-Path $env:USERPROFILE ".gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.15_mapped_official_1.20.1\forge-1.20.1-47.4.15_mapped_official_1.20.1-sources.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($jar)
$mt = $z.Entries | Where-Object { $_.FullName -eq 'net/minecraft/world/inventory/CraftingMenu.java' }
if ($mt) {
    $sr = New-Object System.IO.StreamReader($mt.Open())
    $txt = $sr.ReadToEnd(); $sr.Close()
    $txt -split "`n" | Where-Object { $_ -match 'CraftingContainer|super\(|new CraftingContainer|class CraftingMenu|3, 3|3,3' } | ForEach-Object { Write-Host $_ }
}
$z.Dispose()
