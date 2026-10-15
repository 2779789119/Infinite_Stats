# 迁移 NetworkHandler 到 Forge 26.2 的 ChannelBuilder / SimpleChannel API
$ErrorActionPreference = 'Stop'
$path = "d:\mod\infinitestats-forge-26_2\src\main\java\com\infinitestats\network\NetworkHandler.java"
$enc  = New-Object System.Text.UTF8Encoding $false
$t = [System.IO.File]::ReadAllText($path)

# ---- 1) 收集所有 registerMessage ----
$pat = '(?s)CHANNEL\.registerMessage\(\s*packetId\+\+,\s*(\w+)\.class,\s*(\w+)::encode,\s*(\w+)::decode,\s*(\w+)::handle\s*\);'
$ms = [regex]::Matches($t, $pat)
Write-Output ("packets found: " + $ms.Count)

$adds = New-Object System.Collections.Generic.List[string]
foreach ($m in $ms) {
    $adds.Add("            .add(" + $m.Groups[1].Value + ".class, codec(" + $m.Groups[2].Value +
              "::encode, " + $m.Groups[3].Value + "::decode), " + $m.Groups[4].Value + "::handle)")
}

# ---- 2) 替换 CHANNEL 字段定义 ----
$newChannel = @"
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(Identifier.fromNamespaceAndPath(InfiniteStats.MODID, "main"))
            .networkProtocolVersion(6)
            .optional()
            .simpleChannel()
            .play()
            .bidirectional()
$($adds -join "`r`n")
            .build();

    /** 以旧版 encode/decode 方法适配 26.2 的 StreamCodec。 */
    private static <T> StreamCodec<RegistryFriendlyByteBuf, T> codec(
            BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder) {
        return StreamCodec.of((buf, msg) -> encoder.accept(msg, buf), decoder::apply);
    }
"@

$t = [regex]::Replace($t, '(?s)public static final SimpleChannel CHANNEL = NetworkRegistry\.newSimpleChannel\(.*?\);',
    [System.Text.RegularExpressions.MatchEvaluator]{ param($x) $script:newChannel }, 1)

# ---- 3) 删除旧的 registerMessage 语句（已内联到 CHANNEL 构建） ----
$t = [regex]::Replace($t, $pat, '')

# ---- 4) handle 方法签名与上下文调用 ----
$t = $t.Replace('Supplier<NetworkEvent.Context> ctx', 'CustomPayloadEvent.Context ctx')
$t = $t.Replace('ctx.get().', 'ctx.')

# ---- 5) 打开容器 ----
$t = $t.Replace('NetworkHooks.openScreen(player,', 'player.openMenu(')
$t = $t.Replace('NetworkHooks.openScreen(player ,', 'player.openMenu(')

# ---- 6) import 调整 ----
$t = $t.Replace("import net.minecraftforge.network.NetworkEvent;`r`n", "")
$t = $t.Replace("import net.minecraftforge.network.NetworkRegistry;`r`n", "")
$t = $t.Replace("import net.minecraftforge.network.NetworkHooks;`r`n", "")
$t = $t.Replace("import net.minecraftforge.network.simple.SimpleChannel;",
    "import net.minecraftforge.network.simple.SimpleChannel;`r`n" +
    "import net.minecraftforge.network.ChannelBuilder;`r`n" +
    "import net.minecraftforge.event.network.CustomPayloadEvent;`r`n" +
    "import net.minecraft.network.RegistryFriendlyByteBuf;`r`n" +
    "import net.minecraft.network.codec.StreamCodec;`r`n" +
    "import java.util.function.BiConsumer;`r`n" +
    "import java.util.function.Function;")

# ---- 7) CHANNEL.send(target, msg) -> CHANNEL.send(msg, target) ----
$t = [regex]::Replace($t, '(?s)CHANNEL\.send\(\s*(PacketDistributor\.[A-Z_]+\.(?:with\([^;]*?\)|noArg\(\)))\s*,\s*(.*?)\);',
    [System.Text.RegularExpressions.MatchEvaluator]{ param($x) "CHANNEL.send(" + $x.Groups[2].Value.Trim() + ", " + $x.Groups[1].Value + ");" })

[System.IO.File]::WriteAllText($path, $t, $enc)
Write-Output "migrate-network: done"
