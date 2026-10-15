package com.infinitestats.network;

import com.infinitestats.Config;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 天气控制数据包（客户端 → 服务器）：把**主世界**天气切到指定档位。
 * <p>
 * 天气档位由 {@code WeatherScreen} 传来：{@link #CLEAR} / {@link #RAIN} / {@link #THUNDER}。
 * 传空串时按原版顺序切下一档（保留旧行为，供以后接循环按钮用）。
 * <p>
 * 作用于主世界：玩家在下界 / 末地切天气，改的仍是主世界的天气（与原版指令一致），
 * 所以这个入口在任何维度都有意义。新天气的持续时间取配置 {@code WeatherControl.weatherCycleDuration}。
 */
public final class WeatherControlPacket {

    /** 「晴天」档位 id。 */
    public static final String CLEAR = "clear";
    /** 「下雨」档位 id。 */
    public static final String RAIN = "rain";
    /** 「雷暴」档位 id。 */
    public static final String THUNDER = "thunder";

    /** 目标档位 id；空串 = 切下一档。 */
    private final String weather;

    public WeatherControlPacket() {
        this("");
    }

    public WeatherControlPacket(String weather) {
        this.weather = weather == null ? "" : weather;
    }

    public static void encode(WeatherControlPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.weather);
    }

    public static WeatherControlPacket decode(FriendlyByteBuf buf) {
        return new WeatherControlPacket(buf.readUtf());
    }

    public static void handle(WeatherControlPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
            if (stats == null) return;

            if (!stats.isToggleActive("weather_control")) {
                player.displayClientMessage(
                        Component.translatable("message.infinitestats.weather_locked"), true);
                return;
            }

            ServerLevel level = player.serverLevel().getServer().getLevel(Level.OVERWORLD);
            if (level == null) level = player.serverLevel();
            int duration = Config.WEATHER_CYCLE_DURATION.get();

            String id = msg.weather.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                // 认不出目标档位：按旧行为循环一档
                id = level.isThundering() ? CLEAR : level.isRaining() ? THUNDER : RAIN;
            }

            Component result = switch (id) {
                case CLEAR -> {
                    level.setWeatherParameters(duration, 0, false, false);
                    yield Component.translatable("message.infinitestats.weather.clear");
                }
                case RAIN -> {
                    level.setWeatherParameters(0, duration, true, false);
                    yield Component.translatable("message.infinitestats.weather.rain");
                }
                default -> {
                    level.setWeatherParameters(0, duration, true, true);
                    yield Component.translatable("message.infinitestats.weather.thunder");
                }
            };
            player.displayClientMessage(
                    Component.translatable("message.infinitestats.weather_set", result), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
