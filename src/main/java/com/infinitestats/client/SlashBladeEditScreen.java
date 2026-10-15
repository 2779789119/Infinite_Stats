package com.infinitestats.client;

import com.infinitestats.compat.SlashBladeCompat;
import com.infinitestats.network.EditItemBladePacket;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 拔刀剑编辑面板：改主手那把刀的成长数值。
 * <p>
 * 五项：<b>杀敌数</b> / <b>耀魂数</b> / <b>锻造数</b>（数字输入框）、<b>SA</b>（在全部已注册的特殊攻击里
 * 前后翻）、<b>SE</b>（逗号分隔的 id 列表，下方列出候选）。
 * <p>
 * 为什么 SA 用「前后翻」而不是下拉：这个面板只有一列宽度，而下拉的候选是<b>整个注册表</b>
 * （几十条、名字长度不一），在这么窄的地方做下拉既不好点也不好读；前后翻配上一行显示当前名字，
 * 反而更适合「快速挑一个」这个真实用法（真要精确找某个 SA，用物品编辑器的搜索更合适）。
 * <p>
 * 数值用 {@link EditItemBladePacket#UNCHANGED} 之外的值提交；SA / SE 原样提交（{@code null} 表示不改）。
 * 读取与写入全部走 {@link SlashBladeCompat}（纯反射），没装拔刀剑时本面板根本不会被打开。
 */
public class SlashBladeEditScreen extends PanelScreen {

    private static final int GUI_W = 340;
    private static final int GUI_H = 232;

    private static final int ROW_H = 20;
    private static final int LABEL_W = 76;
    private static final int FIELD_H = 18;
    private static final int GAP = 10;

    private int leftPos, topPos;

    private EditBox killBox, soulBox, refineBox, seBox;
    private Button saPrev, saNext;
    /** 当前 SA 在候选表里的下标；-1 = 用户没动过（提交时不改）。 */
    private int saIndex = -1;
    private List<ResourceLocation> saOptions = new ArrayList<>();
    private List<ResourceLocation> seOptions = new ArrayList<>();

    private final ItemEditorScreen parent;
    private long lastSoundTick;

    public SlashBladeEditScreen(ItemEditorScreen parent) {
        super(Component.translatable("screen.infinitestats.blade_editor"));
        this.parent = parent;
    }

    @Override
    protected int panelW() {
        return GUI_W;
    }

    @Override
    protected int panelH() {
        return GUI_H;
    }

    /** 主手那把刀；不是拔刀剑时返回空栈。 */
    private ItemStack blade() {
        var player = Minecraft.getInstance().player;
        if (player == null) return ItemStack.EMPTY;
        ItemStack stack = player.getMainHandItem();
        return SlashBladeCompat.isBlade(stack) ? stack : ItemStack.EMPTY;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = 0;
        topPos = 0;

        ItemStack blade = blade();
        int fieldX = leftPos + GAP + LABEL_W;
        int fieldW = GUI_W - LABEL_W - GAP * 2;

        killBox = numberBox(fieldX, topPos + 30, fieldW, blade.isEmpty() ? 0 : SlashBladeCompat.killCount(blade));
        soulBox = numberBox(fieldX, topPos + 30 + ROW_H, fieldW, blade.isEmpty() ? 0 : SlashBladeCompat.proudSoul(blade));
        refineBox = numberBox(fieldX, topPos + 30 + ROW_H * 2, fieldW, blade.isEmpty() ? 0 : SlashBladeCompat.refine(blade));

        // SA：前后翻。候选表来自注册表，取不到时按钮直接置灰
        saOptions = SlashBladeCompat.slashArtsOptions();
        String currentSa = blade.isEmpty() ? "" : SlashBladeCompat.slashArts(blade);
        saIndex = indexOfOption(saOptions, currentSa);

        addRenderableWidget(saPrev = Button.builder(Component.literal("<"), b -> cycleSa(-1))
                .bounds(fieldX, topPos + 30 + ROW_H * 3, 20, FIELD_H).build());
        addRenderableWidget(saNext = Button.builder(Component.literal(">"), b -> cycleSa(1))
                .bounds(fieldX + fieldW - 20, topPos + 30 + ROW_H * 3, 20, FIELD_H).build());
        boolean saAvailable = !saOptions.isEmpty();
        saPrev.active = saAvailable;
        saNext.active = saAvailable;

        // SE：逗号分隔的 id 列表（候选在下方灰字提示）
        seOptions = SlashBladeCompat.specialEffectsOptions();
        seBox = new EditBox(font, fieldX, topPos + 30 + ROW_H * 4, fieldW, FIELD_H, Component.empty());
        seBox.setTextColor(EditorUi.PRIMARY);
        seBox.setMaxLength(512);
        seBox.setHint(Component.translatable("screen.infinitestats.blade_editor.se_hint"));
        if (!blade.isEmpty()) {
            seBox.setValue(String.join(", ", SlashBladeCompat.specialEffects(blade)));
        }
        addRenderableWidget(seBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> apply())
                .bounds(leftPos + GUI_W - GAP - 80, topPos + GUI_H - 26, 80, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(leftPos + GUI_W - GAP - 80 - 6 - 70, topPos + GUI_H - 26, 70, FIELD_H).build());
    }

    /** 数字输入框（只允许数字，禁止粘贴负数之外的字符由 responder 兜住）。 */
    private EditBox numberBox(int x, int y, int w, int value) {
        EditBox box = new EditBox(font, x, y, w, FIELD_H, Component.empty());
        box.setTextColor(EditorUi.PRIMARY);
        box.setMaxLength(9);
        box.setValue(Integer.toString(Math.max(0, value)));
        box.setResponder(v -> {
            // 只保留数字：非法字符直接抹掉，省得提交时再解析
            String cleaned = v.replaceAll("[^0-9]", "");
            if (!cleaned.equals(v)) box.setValue(cleaned);
        });
        addRenderableWidget(box);
        return box;
    }

    /** 在候选表里前后翻一格；没动过时从「当前值」的位置起算，当前值不在表里就从表头开始。 */
    private void cycleSa(int delta) {
        if (saOptions.isEmpty()) return;
        if (saIndex < 0) {
            saIndex = delta > 0 ? 0 : saOptions.size() - 1;
        } else {
            saIndex = (saIndex + delta + saOptions.size()) % saOptions.size();
        }
        playClick();
    }

    /** 当前选中的 SA 的 id 字符串；没动过返回 {@code null}（提交时表示「不改」）。 */
    private String selectedSa() {
        if (saIndex < 0 || saOptions.isEmpty()) return null;
        return saOptions.get(Math.floorMod(saIndex, saOptions.size())).toString();
    }

    private void apply() {
        int kill = parse(killBox, SlashBladeCompat.killCount(blade()));
        int soul = parse(soulBox, SlashBladeCompat.proudSoul(blade()));
        int refine = parse(refineBox, SlashBladeCompat.refine(blade()));

        List<String> se = new ArrayList<>();
        for (String raw : seBox.getValue().split(",")) {
            String id = raw.trim();
            if (id.isEmpty()) continue;
            if (ResourceLocation.tryParse(id) != null) se.add(id);
        }

        NetworkHandler.CHANNEL.sendToServer(
                new EditItemBladePacket(kill, soul, refine, selectedSa(), se));
        playClick();
        onClose();
    }

    /** 解析数字框；解析不出来就用原值（等于「不改这一项」）。 */
    private static int parse(EditBox box, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(box.getValue().trim()));
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** 按像素宽度截断文本，超长时补省略号（SA 名字与 SE 候选都可能很长）。 */
    private String trim(String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        int limit = maxWidth - font.width("…");
        if (limit <= 0) return "…";
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            int cw = font.width(text.substring(i, i + 1));
            if (w + cw > limit) break;
            w += cw;
            sb.append(text.charAt(i));
        }
        return sb + "…";
    }

    /** 选项表里某个 id 的下标；不在表里返回 -1。 */
    private static int indexOfOption(List<ResourceLocation> options, String id) {
        if (id == null || id.isEmpty() || options.isEmpty()) return -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).toString().equals(id)) return i;
        }
        return -1;
    }

    private void playClick() {
        if (minecraft == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());

        int labelX = leftPos + GAP;
        int textY = topPos + 30 + (FIELD_H - font.lineHeight) / 2 + 1;
        g.drawString(font, Component.translatable("screen.infinitestats.blade_editor.kill").getString(),
                labelX, textY, EditorUi.SECONDARY);
        g.drawString(font, Component.translatable("screen.infinitestats.blade_editor.soul").getString(),
                labelX, textY + ROW_H, EditorUi.SECONDARY);
        g.drawString(font, Component.translatable("screen.infinitestats.blade_editor.refine").getString(),
                labelX, textY + ROW_H * 2, EditorUi.SECONDARY);
        g.drawString(font, Component.translatable("screen.infinitestats.blade_editor.sa").getString(),
                labelX, textY + ROW_H * 3, EditorUi.SECONDARY);
        g.drawString(font, Component.translatable("screen.infinitestats.blade_editor.se").getString(),
                labelX, textY + ROW_H * 4, EditorUi.SECONDARY);

        // SA 当前值（夹在两个箭头中间）
        String saText;
        if (saOptions.isEmpty()) {
            saText = Component.translatable("screen.infinitestats.blade_editor.unavailable").getString();
        } else if (saIndex < 0) {
            saText = Component.translatable("screen.infinitestats.blade_editor.unchanged").getString();
        } else {
            ResourceLocation rl = saOptions.get(Math.floorMod(saIndex, saOptions.size()));
            saText = SlashBladeCompat.slashArtsName(rl);
        }
        int saX = leftPos + GAP + LABEL_W + 24;
        int saW = GUI_W - LABEL_W - GAP * 2 - 48;
        g.drawString(font, trim(saText, saW), saX,
                topPos + 30 + ROW_H * 3 + (FIELD_H - font.lineHeight) / 2 + 1,
                saIndex < 0 ? EditorUi.SECONDARY : EditorUi.PRIMARY);

        // SE 候选提示（只列前几条，够玩家知道该填什么）
        if (!seOptions.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, seOptions.size()); i++) {
                if (i > 0) sb.append("  ");
                sb.append(seOptions.get(i).getPath());
            }
            if (seOptions.size() > 3) sb.append("  +").append(seOptions.size() - 3);
            g.drawString(font, trim(sb.toString(), GUI_W - GAP * 2),
                    leftPos + GAP, topPos + 30 + ROW_H * 5 + 4, EditorUi.SECONDARY);
        }

        if (blade().isEmpty()) {
            g.drawString(font, Component.translatable("screen.infinitestats.blade_editor.no_blade").getString(),
                    leftPos + GAP, topPos + GUI_H - 40, EditorUi.DANGER);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
