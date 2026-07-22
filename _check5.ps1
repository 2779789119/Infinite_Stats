Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = Join-Path $env:USERPROFILE ".gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.15_mapped_official_1.20.1\forge-1.20.1-47.4.15_mapped_official_1.20.1-sources.jar"
$z = [System.IO.Compression.ZipFile]::OpenRead($jar)
$targets = @('net/minecraftforge/network/NetworkHooks.java','net/minecraft/world/INamedContainerProvider.java','net/minecraftforge/common/extensions/IForgeMenuProvider.java')
foreach ($t in $targets) {
    $e = $z.Entries | Where-Object { $_.FullName -eq $t }
    if ($e) {
        Write-Host ("===== " + $t)
        $sr = New-Object System.IO.StreamReader($e.Open())
        $txt = $sr.ReadToEnd(); $sr.Close()
        $txt -split "`n" | Where-Object { $_ -match 'openScreen|getMenuType|MenuType|MenuProvider|interface ' } | ForEach-Object { Write-Host $_ }
    } else { Write-Host ("MISSING " + $t) }
}
$z.Dispose()
