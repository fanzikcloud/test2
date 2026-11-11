package dev.fanzik.client.ui;

import dev.fanzik.client.FanzikClient;
import dev.fanzik.client.config.GuiState;
import dev.fanzik.client.input.KeyNames;
import dev.fanzik.client.module.Category;
import dev.fanzik.client.module.Module;
import dev.fanzik.client.render.Icons;
import dev.fanzik.client.render.Palette;
import dev.fanzik.client.render.Render2D;
import dev.fanzik.client.setting.BooleanSetting;
import dev.fanzik.client.setting.EnumSetting;
import dev.fanzik.client.setting.NumberSetting;
import dev.fanzik.client.setting.Setting;
import dev.fanzik.client.util.Argb;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClickGuiScreen extends Screen {
    public static final int WIDTH = 880;
    public static final int HEIGHT = 512;
    private static final float LIST_X = 212;
    private static final float LIST_Y = 146;
    private static final float LIST_W = 296;
    private static final float LIST_H = 316;
    private static final float SETTINGS_Y = 214;
    private static final float SETTINGS_H = 202;
    private final FanzikClient fanzik;
    private final Map<String, Float> toggleAnimations = new HashMap<>();
    private TextFieldWidget search;
    private Module selected;
    private Module binding;
    private ConfigOverlay configOverlay;
    private NumberSetting draggingSlider;
    private float sliderX;
    private float sliderWidth;
    private float originX;
    private float originY;
    private float viewScale = 1;
    private float openProgress;
    private float categoryIndicator = 167;
    private boolean draggingWindow;
    private double dragOffsetX;
    private double dragOffsetY;
    private double cardScroll;
    private double settingsScroll;
    private long lastFrame = System.nanoTime();
    private String tooltip;

    public ClickGuiScreen(FanzikClient fanzik) {
        super(Text.literal("Fanzik Client"));
        this.fanzik = fanzik;
        categoryIndicator = 121 + fanzik.preferences().snapshot().category().ordinal() * 46;
    }

    @Override
    protected void init() {
        String query = search == null ? "" : search.getText();
        search = UiControls.field(client, 245, 31, 324, 18, "ui.fanzik.search");
        search.setMaxLength(48);
        search.setText(query);
        search.setChangedListener(ignored -> {
            cardScroll = 0;
            reconcileSelection();
        });
        reconcileSelection();
        layout();
    }

    public Module selectedModule() {
        return selected;
    }

    private List<Module> visibleModules() {
        return fanzik.modules().search(fanzik.preferences().snapshot().category(), search == null ? "" : search.getText());
    }

    private void reconcileSelection() {
        List<Module> modules = visibleModules();
        if (!modules.contains(selected)) {
            selected = modules.isEmpty() ? null : modules.getFirst();
            binding = null;
            draggingSlider = null;
            settingsScroll = 0;
        }
        cardScroll = Math.clamp(cardScroll, 0, Math.max(0, modules.size() * 104 - LIST_H));
    }

    private void layout() {
        float fit = Math.max(0.1f, Math.min(1, Math.min((width - 24f) / WIDTH, (height - 24f) / HEIGHT)));
        viewScale = fit * (0.97f + 0.03f * openProgress);
        GuiState state = fanzik.preferences().snapshot();
        originX = (float) ((width - WIDTH * fit) * state.horizontalPosition()) + WIDTH * (fit - viewScale) / 2;
        originY = (float) ((height - HEIGHT * fit) * state.verticalPosition()) + HEIGHT * (fit - viewScale) / 2;
    }

    private double localX(double x) {
        return (x - originX) / viewScale;
    }

    private double localY(double y) {
        return (y - originY) / viewScale;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        double elapsed = Math.min(0.1, (now - lastFrame) / 1.0e9);
        lastFrame = now;
        openProgress = UiControls.smooth(openProgress, 1, 18, elapsed);
        layout();
        reconcileSelection();
        double x = localX(mouseX);
        double y = localY(mouseY);
        int accent = fanzik.preferences().snapshot().palette().accent();
        tooltip = null;
        try (Render2D r = new Render2D(context)) {
            r.gradient(0, 0, width, height, 0xA50A0D17, 0xD0070A12);
            try (Render2D.Scope transform = r.transform(originX, originY, viewScale);
                 Render2D.Scope opacity = r.opacity(openProgress)) {
                renderWindow(r, accent);
                renderSidebar(r, x, y, accent, elapsed);
                renderHeader(r, x, y, delta, accent);
                renderContent(r, x, y, accent, elapsed);
                renderFooter(r, accent);
                if (configOverlay != null) {
                    configOverlay.render(r, x, y, delta, accent);
                } else if (tooltip != null) {
                    renderTooltip(r, x, y);
                }
            }
            fanzik.notifications().render(r, width, height, accent);
        }
    }

    private void renderWindow(Render2D r, int accent) {
        r.shadow(0, 0, WIDTH, HEIGHT, 18, 19, 0xFF000000);
        r.roundedGradient(0, 0, WIDTH, HEIGHT, 18, 0xFF141927, 0xFF0E121D);
        r.rounded(1, 1, 187, HEIGHT - 2, 17, 0xFF0D111C);
        r.rect(169, 1, 19, HEIGHT - 2, 0xFF0D111C);
        r.rect(188, 20, 0.8f, HEIGHT - 40, Theme.BORDER);
        r.roundedGradient(207, 1, WIDTH - 228, 73, 14, Argb.alpha(accent, 0.045), 0x00000000);
        r.stroke(0, 0, WIDTH, HEIGHT, 18, 0.8f, 0x20FFFFFF);
        r.rounded(20, 23, 30, 30, 9, Argb.alpha(accent, 0.15));
        Icons.logo(r, 25, 28, 20, accent);
        r.text("FANZIK", 61, 22, 1.42f, Theme.TEXT);
        r.text("CLIENT  /  " + FanzikClient.VERSION, 62, 43, 0.72f, Theme.MUTED);
    }

    private void renderSidebar(Render2D r, double x, double y, int accent, double elapsed) {
        Category current = fanzik.preferences().snapshot().category();
        r.text(I18n.translate("ui.fanzik.categories"), 24, 94, 0.75f, Theme.MUTED);
        categoryIndicator = UiControls.smooth(categoryIndicator, 121 + current.ordinal() * 46, 14, elapsed);
        r.rounded(14, categoryIndicator, 160, 38, 8, Argb.alpha(accent, 0.12));
        r.rounded(14, categoryIndicator + 11, 2.5f, 16, 1.2f, accent);
        for (Category category : Category.values()) {
            float rowY = 121 + category.ordinal() * 46;
            boolean chosen = category == current;
            boolean hover = UiControls.hit(x, y, 14, rowY, 160, 38);
            if (hover && !chosen) {
                r.rounded(14, rowY, 160, 38, 8, 0x0AFFFFFF);
            }
            int color = chosen ? accent : hover ? Theme.TEXT : Theme.SECONDARY;
            Icons.category(r, category, 26, rowY + 10, 18, color);
            r.text(category.label(), 55, rowY + 12, color);
            String count = Integer.toString(fanzik.modules().in(category).size());
            r.rounded(145, rowY + 11, 18, 17, 5, chosen ? Argb.alpha(accent, 0.11) : 0x0BFFFFFF);
            r.centered(count, 154, rowY + 14, 0.8f, chosen ? accent : Theme.MUTED);
        }
        r.rounded(19, 403, 149, 48, 8, 0xFF141A28);
        r.circle(33, 420, 2.5f, Theme.SUCCESS);
        r.text(I18n.translate("ui.fanzik.in_game"), 43, 414, 0.9f, Theme.SECONDARY);
        r.text(I18n.translate("ui.fanzik.active", fanzik.modules().enabledCount()), 31, 434, 0.8f, Theme.MUTED);
        r.text("1.21.1  ·  FABRIC", 24, 481, 0.73f, Theme.MUTED);
        for (Palette palette : Palette.values()) {
            float paletteX = 118 + palette.ordinal() * 18;
            r.circle(paletteX, 470, 5, palette.accent());
            if (palette == fanzik.preferences().snapshot().palette()) {
                r.arc(paletteX, 470, 7.5f, 0, Math.PI * 2, 1, Argb.alpha(palette.accent(), 0.7));
            }
            if (UiControls.hit(x, y, paletteX - 8, 462, 16, 16)) {
                tooltip = I18n.translate(palette.translationKey());
            }
        }
        r.text(I18n.translate("ui.fanzik.accent"), 24, 466, 0.8f, Theme.MUTED);
    }

    private void renderHeader(Render2D r, double x, double y, float delta, int accent) {
        r.rounded(212, 21, 370, 33, 8, search.isFocused() ? 0xFF1A2030 : 0xFF101521);
        r.stroke(212, 21, 370, 33, 8, 0.7f, search.isFocused() ? Argb.alpha(accent, 0.5) : Theme.BORDER);
        Icons.search(r, 224, 30, search.isFocused() ? accent : Theme.MUTED);
        search.render(r.context(), (int) x, (int) y, delta);
        UiControls.button(r, I18n.translate("ui.fanzik.configs"), 596, 21, 111, 33, x, y, accent, false, true);
        r.rounded(736, 27, 67, 21, 5, 0x0AFFFFFF);
        r.centered(fanzik.menuKeyLabel(), 769.5f, 32, 0.75f, Theme.MUTED);
        boolean closeHover = UiControls.hit(x, y, 825, 22, 30, 30);
        if (closeHover) {
            r.rounded(825, 22, 30, 30, 7, 0x14FFFFFF);
        }
        Icons.close(r, 834, 31, closeHover ? Theme.TEXT : Theme.MUTED);
    }

    private void renderContent(Render2D r, double x, double y, int accent, double elapsed) {
        Category category = fanzik.preferences().snapshot().category();
        List<Module> modules = visibleModules();
        boolean searching = !search.getText().isBlank();
        r.text(searching ? I18n.translate("ui.fanzik.search_results") : category.label(), LIST_X, 89, 1.65f, Theme.TEXT);
        r.text(searching ? I18n.translate("ui.fanzik.results", modules.size()) : I18n.translate(category.descriptionKey()),
                LIST_X + 1, 115, 0.9f, Theme.MUTED);
        if (modules.isEmpty()) {
            renderEmpty(r, x, y, category, searching, accent);
            return;
        }
        try (Render2D.Scope ignored = r.scissor(LIST_X - 2, LIST_Y - 1, LIST_W + 4, LIST_H + 2)) {
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                float cardY = LIST_Y + i * 104 - (float) cardScroll;
                if (cardY + 96 < LIST_Y || cardY > LIST_Y + LIST_H) {
                    continue;
                }
                renderCard(r, module, cardY, x, y, accent, elapsed);
            }
        }
        renderInspector(r, x, y, accent, elapsed);
    }

    private void renderCard(Render2D r, Module module, float cardY, double x, double y, int accent, double elapsed) {
        boolean chosen = selected == module;
        boolean hover = UiControls.hit(x, y, LIST_X, cardY, LIST_W, 96) && UiControls.hit(x, y, LIST_X, LIST_Y, LIST_W, LIST_H);
        r.roundedGradient(LIST_X, cardY, LIST_W, 96, 11, chosen ? 0xFF1D2337 : hover ? 0xFF1A2031 : Theme.PANEL, Theme.PANEL);
        r.stroke(LIST_X, cardY, LIST_W, 96, 11, 0.8f, chosen ? Argb.alpha(accent, 0.6) : Theme.BORDER);
        r.rounded(LIST_X + 15, cardY + 16, 32, 32, 9, Argb.alpha(accent, 0.11));
        Icons.category(r, module.category(), LIST_X + 22, cardY + 23, 18, accent);
        r.text(r.elide(I18n.translate(module.nameKey()), 167), LIST_X + 59, cardY + 16, 1.1f, Theme.TEXT);
        r.text(module.enabled() ? I18n.translate("ui.fanzik.enabled") : I18n.translate("ui.fanzik.disabled"),
                LIST_X + 60, cardY + 35, 0.8f, module.enabled() ? Theme.SUCCESS : Theme.MUTED);
        float animation = UiControls.smooth(toggleAnimations.getOrDefault(module.id(), module.enabled() ? 1f : 0f),
                module.enabled() ? 1 : 0, 14, elapsed);
        toggleAnimations.put(module.id(), animation);
        UiControls.toggle(r, LIST_X + LIST_W - 50, cardY + 17, animation, accent);
        r.text(r.elide(I18n.translate(module.descriptionKey()), LIST_W - 30), LIST_X + 15, cardY + 64, 0.9f, Theme.SECONDARY);
        if (hover && !UiControls.hit(x, y, LIST_X + LIST_W - 57, cardY + 10, 48, 33)) {
            tooltip = I18n.translate("ui.fanzik.module_hint");
        }
    }

    private void renderInspector(Render2D r, double x, double y, int accent, double elapsed) {
        if (selected == null) {
            return;
        }
        r.rounded(528, 89, 330, 373, 12, 0xFF121825);
        r.stroke(528, 89, 330, 373, 12, 0.7f, Theme.BORDER);
        r.text(I18n.translate(selected.nameKey()), 546, 108, 1.25f, Theme.TEXT);
        r.text(I18n.translate("ui.fanzik.module_settings"), 547, 129, 0.8f, Theme.MUTED);
        r.circle(836, 115, 3, selected.enabled() ? Theme.SUCCESS : Theme.MUTED);
        r.rect(546, 150, 294, 0.7f, Theme.BORDER);
        r.text(I18n.translate("ui.fanzik.keybind"), 547, 168, Theme.SECONDARY);
        boolean capturing = binding == selected;
        String keyLabel = capturing ? "…" : selected.key() == GLFW.GLFW_KEY_UNKNOWN ? I18n.translate("ui.fanzik.none") : KeyNames.label(selected.key());
        UiControls.button(r, r.elide(keyLabel, 91), 738, 160, 102, 27, x, y, accent, capturing, true);
        if (UiControls.hit(x, y, 546, 157, 294, 34)) {
            tooltip = I18n.translate(capturing ? "ui.fanzik.bind_capture_hint" : "ui.fanzik.bind_hint");
        }
        r.text(I18n.translate(capturing ? "ui.fanzik.press_key" : "ui.fanzik.settings"), 547, 201, 0.75f, capturing ? accent : Theme.MUTED);
        List<Setting<?>> settings = selected.settings().stream().filter(Setting::visible).toList();
        float contentHeight = 0;
        for (Setting<?> setting : settings) {
            contentHeight += rowHeight(setting);
        }
        settingsScroll = Math.clamp(settingsScroll, 0, Math.max(0, contentHeight - SETTINGS_H));
        float rowY = SETTINGS_Y - (float) settingsScroll;
        try (Render2D.Scope ignored = r.scissor(545, SETTINGS_Y, 296, SETTINGS_H)) {
            for (Setting<?> setting : settings) {
                float rowHeight = rowHeight(setting);
                if (rowY + rowHeight >= SETTINGS_Y && rowY <= SETTINGS_Y + SETTINGS_H) {
                    renderSetting(r, setting, rowY, x, y, accent);
                }
                rowY += rowHeight;
            }
        }
        if (contentHeight > SETTINGS_H) {
            float thumbHeight = SETTINGS_H * SETTINGS_H / contentHeight;
            float thumbY = SETTINGS_Y + (float) (settingsScroll / (contentHeight - SETTINGS_H)) * (SETTINGS_H - thumbHeight);
            r.rounded(849, thumbY, 2, thumbHeight, 1, Argb.alpha(accent, 0.45));
        }
        UiControls.button(r, I18n.translate("ui.fanzik.reset_settings"), 546, 430, 294, 24, x, y, accent, false, true);
    }

    private void renderSetting(Render2D r, Setting<?> setting, float rowY, double x, double y, int accent) {
        r.text(r.elide(I18n.translate(setting.nameKey()), setting instanceof EnumSetting<?> ? 155 : 238), 547, rowY + 7, 0.9f, Theme.SECONDARY);
        if (setting instanceof BooleanSetting booleanSetting) {
            String animationKey = selected.id() + ":" + setting.id();
            float value = UiControls.smooth(toggleAnimations.getOrDefault(animationKey, booleanSetting.get() ? 1f : 0f),
                    booleanSetting.get() ? 1 : 0, 0.28f, 0.1);
            toggleAnimations.put(animationKey, value);
            UiControls.toggle(r, 806, rowY + 2, value, accent);
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            String label = I18n.translate(enumSetting.valueKey());
            r.rounded(714, rowY + 1, 126, 25, 6, UiControls.hit(x, y, 714, rowY + 1, 126, 25) ? Theme.HOVER : 0xFF1A2131);
            r.text(r.elide(label, 102), 723, rowY + 8, 0.82f, accent);
            Icons.chevron(r, 824, rowY + 10, Theme.MUTED);
        } else if (setting instanceof NumberSetting numberSetting) {
            String value = numberSetting.formatted();
            r.text(value, 838 - r.textWidth(value, 0.9f), rowY + 7, 0.9f, accent);
            r.rounded(549, rowY + 33, 288, 3.5f, 1.75f, 0xFF2D354A);
            float filled = (float) numberSetting.fraction() * 288;
            r.rounded(549, rowY + 33, Math.max(2, filled), 3.5f, 1.75f, accent);
            r.circle(549 + filled, rowY + 34.75f, draggingSlider == setting ? 5 : 4, accent);
        }
        if (UiControls.hit(x, y, 546, rowY, 294, rowHeight(setting)) && UiControls.hit(x, y, 546, SETTINGS_Y, 294, SETTINGS_H)) {
            tooltip = I18n.translate(setting.descriptionKey());
        }
    }

    private static float rowHeight(Setting<?> setting) {
        return setting instanceof NumberSetting ? 60 : setting instanceof EnumSetting<?> ? 48 : 42;
    }

    private void renderEmpty(Render2D r, double x, double y, Category category, boolean searching, int accent) {
        r.circle(534, 246, 35, Argb.alpha(accent, 0.07));
        if (searching) {
            try (Render2D.Scope ignored = r.transform(520, 232, 2)) {
                Icons.search(r, 0, 0, Argb.alpha(accent, 0.65));
            }
        } else {
            Icons.category(r, category, 516, 228, 36, Argb.alpha(accent, 0.7));
        }
        r.centered(I18n.translate(searching ? "ui.fanzik.nothing_found" : "ui.fanzik.category_empty", category.label()), 534, 302, 1.2f, Theme.TEXT);
        r.centered(I18n.translate(searching ? "ui.fanzik.try_search" : "ui.fanzik.category_empty.description"), 534, 326, 0.9f, Theme.MUTED);
        if (!searching && category != Category.MOVEMENT) {
            UiControls.button(r, I18n.translate("ui.fanzik.open_movement"), 428, 357, 212, 31, x, y, accent, true, true);
        }
    }

    private void renderFooter(Render2D r, int accent) {
        r.rect(212, 477, 646, 0.7f, Theme.BORDER);
        r.circle(216, 493, 2, fanzik.configs().pending() ? accent : Theme.SUCCESS);
        r.text(I18n.translate(fanzik.configs().pending() ? "ui.fanzik.saving" : "ui.fanzik.autosaved"), 226, 488, 0.8f, Theme.MUTED);
        String help = I18n.translate("ui.fanzik.footer_hint");
        r.text(help, 857 - r.textWidth(help, 0.75f), 489, 0.75f, Theme.MUTED);
    }

    private void renderTooltip(Render2D r, double x, double y) {
        float maxWidth = 236;
        int lines = client.textRenderer.wrapLines(Render2D.styled(tooltip), (int) maxWidth).size();
        float h = lines * 12 + 18;
        float tx = (float) Math.clamp(x + 12, 8, WIDTH - maxWidth - 24);
        float ty = (float) Math.clamp(y + 15, 8, HEIGHT - h - 8);
        r.shadow(tx, ty, maxWidth + 18, h, 7, 6, 0xC0000000);
        r.rounded(tx, ty, maxWidth + 18, h, 7, 0xFE202739);
        r.stroke(tx, ty, maxWidth + 18, h, 7, 0.7f, 0x25FFFFFF);
        r.paragraph(tooltip, tx + 9, ty + 8, maxWidth, 12, Theme.SECONDARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = localX(mouseX);
        double y = localY(mouseY);
        if (configOverlay != null) {
            return configOverlay.mouseClicked(x, y, button);
        }
        search.setFocused(UiControls.hit(x, y, 212, 21, 370, 33));
        if (search.isFocused()) {
            search.mouseClicked(x, y, button);
            return true;
        }
        if (UiControls.hit(x, y, 825, 22, 30, 30)) {
            close();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && UiControls.hit(x, y, 596, 21, 111, 33)) {
            binding = null;
            configOverlay = new ConfigOverlay(fanzik, () -> configOverlay = null);
            return true;
        }
        for (Category category : Category.values()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && UiControls.hit(x, y, 14, 121 + category.ordinal() * 46, 160, 38)) {
                chooseCategory(category);
                return true;
            }
        }
        for (Palette palette : Palette.values()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && UiControls.hit(x, y, 110 + palette.ordinal() * 18, 462, 16, 16)) {
                fanzik.preferences().palette(palette);
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && UiControls.hit(x, y, 428, 357, 212, 31)
                && visibleModules().isEmpty() && search.getText().isBlank()) {
            chooseCategory(Category.MOVEMENT);
            return true;
        }
        if (UiControls.hit(x, y, LIST_X, LIST_Y, LIST_W, LIST_H)) {
            List<Module> modules = visibleModules();
            int index = (int) ((y - LIST_Y + cardScroll) / 104);
            if (index >= 0 && index < modules.size()) {
                float cardY = LIST_Y + index * 104 - (float) cardScroll;
                if (y < cardY + 96) {
                    Module clicked = modules.get(index);
                    if (selected != clicked) {
                        binding = null;
                        settingsScroll = 0;
                    }
                    selected = clicked;
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && UiControls.hit(x, y, LIST_X + LIST_W - 57, cardY + 10, 48, 33)) {
                        fanzik.setModuleEnabled(selected, !selected.enabled());
                    }
                    return true;
                }
            }
        }
        if (selected != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && UiControls.hit(x, y, 738, 160, 102, 27)) {
                binding = binding == selected ? null : selected;
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && UiControls.hit(x, y, 546, 430, 294, 24)) {
                selected.resetSettings();
                return true;
            }
            float rowY = SETTINGS_Y - (float) settingsScroll;
            for (Setting<?> setting : selected.settings().stream().filter(Setting::visible).toList()) {
                if (UiControls.hit(x, y, 546, rowY, 294, rowHeight(setting)) && UiControls.hit(x, y, 546, SETTINGS_Y, 294, SETTINGS_H)) {
                    if (setting instanceof BooleanSetting bool && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        bool.toggle();
                    } else if (setting instanceof EnumSetting<?> enumeration && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                        enumeration.cycle(button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? 1 : -1);
                    } else if (setting instanceof NumberSetting number && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        draggingSlider = number;
                        sliderX = 549;
                        sliderWidth = 288;
                        updateSlider(x);
                    }
                    return true;
                }
                rowY += rowHeight(setting);
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (UiControls.hit(x, y, 0, 0, 188, 75) || UiControls.hit(x, y, 710, 0, 114, 70))) {
            draggingWindow = true;
            dragOffsetX = mouseX - originX;
            dragOffsetY = mouseY - originY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void chooseCategory(Category category) {
        fanzik.preferences().category(category);
        search.setText("");
        cardScroll = 0;
        settingsScroll = 0;
        binding = null;
        reconcileSelection();
    }

    private void updateSlider(double x) {
        if (draggingSlider != null) {
            draggingSlider.setFraction((x - sliderX) / sliderWidth);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (configOverlay != null) {
            return true;
        }
        if (draggingSlider != null) {
            updateSlider(localX(mouseX));
            return true;
        }
        if (draggingWindow) {
            double remainingX = width - WIDTH * viewScale;
            double remainingY = height - HEIGHT * viewScale;
            fanzik.preferences().position(remainingX <= 1 ? 0.5 : (mouseX - dragOffsetX) / remainingX,
                    remainingY <= 1 ? 0.5 : (mouseY - dragOffsetY) / remainingY);
            layout();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingWindow = false;
        draggingSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double x = localX(mouseX);
        double y = localY(mouseY);
        if (configOverlay != null) {
            return configOverlay.mouseScrolled(x, y, verticalAmount);
        }
        if (UiControls.hit(x, y, LIST_X, LIST_Y, LIST_W, LIST_H)) {
            cardScroll = Math.clamp(cardScroll - verticalAmount * 28, 0, Math.max(0, visibleModules().size() * 104 - LIST_H));
            return true;
        }
        if (selected != null && UiControls.hit(x, y, 546, SETTINGS_Y, 294, SETTINGS_H)) {
            double contentHeight = selected.settings().stream().filter(Setting::visible).mapToDouble(ClickGuiScreen::rowHeight).sum();
            settingsScroll = Math.clamp(settingsScroll - verticalAmount * 22, 0, Math.max(0, contentHeight - SETTINGS_H));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (configOverlay != null) {
            return configOverlay.keyPressed(key, scanCode, modifiers);
        }
        if (binding != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                binding = null;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                binding.key(GLFW.GLFW_KEY_UNKNOWN);
                binding = null;
            } else if (fanzik.isMenuKey(key, scanCode)) {
                binding = null;
            } else if (key >= 32 && key <= GLFW.GLFW_KEY_LAST) {
                binding.key(key);
                binding = null;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || fanzik.isMenuKey(key, scanCode)) {
            close();
            return true;
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_F) {
            search.setFocused(true);
            return true;
        }
        if (search.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return configOverlay != null ? configOverlay.charTyped(character, modifiers) : search.charTyped(character, modifiers);
    }

    @Override
    public void tick() {
        if (client == null || client.world == null || client.player == null) {
            close();
        }
    }

    @Override
    public void close() {
        binding = null;
        draggingSlider = null;
        draggingWindow = false;
        configOverlay = null;
        if (client != null) {
            client.setScreen(null);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
