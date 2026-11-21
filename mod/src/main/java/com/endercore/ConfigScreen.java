package com.endercore;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * EnderCore 配置 GUI — 整页可滚动大列表，内嵌动作文件子列表
 * LLM 接入参数（Base URL / API Key / 模型 / 决策间隔）在此界面填写，保存到本地配置，不打包进 mod。
 */
public class ConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget hostField;
    private TextFieldWidget portField;
    private TextFieldWidget urlField;
    private TextFieldWidget keyField;
    private TextFieldWidget modelField;
    private TextFieldWidget intervalField;
    private ButtonWidget saveBtn, closeBtn, delBtn, openBtn, refreshBtn;

    private final List<String> modelNames = new ArrayList<>();
    private int selectedIndex = -1;
    private int modelScroll = 0;
    private int masterScroll = 0;

    private static final int BOX_X = 50;
    private static final int BOX_W = 300;
    private static final int FIELD_W = 200;
    private static final int ROW_H = 12;
    private static final int LIST_H = 110;

    // 虚拟内容坐标
    private static final int Y_HOST_FLD = 32;
    private static final int Y_PORT_FLD = 62;
    private static final int Y_URL_FLD = 92;
    private static final int Y_KEY_FLD = 122;
    private static final int Y_MODEL_FLD = 152;
    private static final int Y_INTERVAL_FLD = 182;
    private static final int Y_BTNS1 = 202;
    private static final int Y_LIST_BOX = 236;
    private static final int Y_BTNS2 = 352;
    private static final int Y_DROP = 370;
    private static final int CONTENT_H = 400;

    public ConfigScreen(Screen parent) {
        super(Text.literal("EnderCore 配置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int fx = cx - FIELD_W / 2;

        this.hostField = new TextFieldWidget(this.textRenderer, fx, 0, FIELD_W, 14, Text.literal("HTTP Host"));
        this.hostField.setMaxLength(32);
        this.hostField.setText(Config.get().http_host);
        this.addDrawableChild(this.hostField);

        this.portField = new TextFieldWidget(this.textRenderer, fx, 0, FIELD_W, 14, Text.literal("HTTP Port"));
        this.portField.setMaxLength(8);
        this.portField.setText(String.valueOf(Config.get().http_port));
        this.addDrawableChild(this.portField);

        this.urlField = new TextFieldWidget(this.textRenderer, fx, 0, FIELD_W, 14, Text.literal("LLM Base URL"));
        this.urlField.setMaxLength(200);
        this.urlField.setText(Config.get().llm_base_url);
        this.addDrawableChild(this.urlField);

        this.keyField = new TextFieldWidget(this.textRenderer, fx, 0, FIELD_W, 14, Text.literal("LLM API Key"));
        this.keyField.setMaxLength(300);
        this.keyField.setText(Config.get().llm_api_key);
        this.addDrawableChild(this.keyField);

        this.modelField = new TextFieldWidget(this.textRenderer, fx, 0, FIELD_W, 14, Text.literal("LLM Model"));
        this.modelField.setMaxLength(100);
        this.modelField.setText(Config.get().llm_model);
        this.addDrawableChild(this.modelField);

        this.intervalField = new TextFieldWidget(this.textRenderer, fx, 0, FIELD_W, 14, Text.literal("Decision Interval (s)"));
        this.intervalField.setMaxLength(8);
        this.intervalField.setText(String.valueOf(Config.get().llm_decision_interval_sec));
        this.addDrawableChild(this.intervalField);

        refreshList();

        this.saveBtn = ButtonWidget.builder(Text.literal("§a保存"), btn -> save()).dimensions(0, 0, 95, 14).build();
        this.closeBtn = ButtonWidget.builder(Text.literal("§c关闭"), btn -> close()).dimensions(0, 0, 95, 14).build();
        this.delBtn = ButtonWidget.builder(Text.literal("§4删除"), btn -> deleteSelected()).dimensions(0, 0, 55, 12).build();
        this.openBtn = ButtonWidget.builder(Text.literal("§e打开文件夹"), btn -> openFolder()).dimensions(0, 0, 80, 12).build();
        this.refreshBtn = ButtonWidget.builder(Text.literal("§b刷新"), btn -> refreshList()).dimensions(0, 0, 45, 12).build();

        this.addDrawableChild(saveBtn);
        this.addDrawableChild(closeBtn);
        this.addDrawableChild(delBtn);
        this.addDrawableChild(openBtn);
        this.addDrawableChild(refreshBtn);

        applyScroll();
    }

    private void applyScroll() {
        int cx = this.width / 2;
        int fx = cx - FIELD_W / 2;
        hostField.setX(fx); hostField.setY(Y_HOST_FLD - masterScroll);
        portField.setX(fx); portField.setY(Y_PORT_FLD - masterScroll);
        urlField.setX(fx); urlField.setY(Y_URL_FLD - masterScroll);
        keyField.setX(fx); keyField.setY(Y_KEY_FLD - masterScroll);
        modelField.setX(fx); modelField.setY(Y_MODEL_FLD - masterScroll);
        intervalField.setX(fx); intervalField.setY(Y_INTERVAL_FLD - masterScroll);
        saveBtn.setX(fx); saveBtn.setY(Y_BTNS1 - masterScroll);
        closeBtn.setX(fx + 105); closeBtn.setY(Y_BTNS1 - masterScroll);
        delBtn.setX(BOX_X); delBtn.setY(Y_BTNS2 - masterScroll);
        openBtn.setX(BOX_X + 60); openBtn.setY(Y_BTNS2 - masterScroll);
        refreshBtn.setX(BOX_X + 145); refreshBtn.setY(Y_BTNS2 - masterScroll);
    }

    private void refreshList() {
        modelNames.clear();
        File[] files = Config.getModelsDir().listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) for (File f : files) modelNames.add(f.getName());
        if (selectedIndex >= modelNames.size()) selectedIndex = -1;
    }

    private void deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= modelNames.size()) return;
        File f = new File(Config.getModelsDir(), modelNames.get(selectedIndex));
        if (f.exists()) f.delete();
        refreshList();
    }

    private void openFolder() {
        try {
            String path = Config.getModelsDir().getAbsolutePath();
            Runtime.getRuntime().exec(new String[]{"explorer.exe", path});
        } catch (Exception e) {
            EnderCoreMod.LOGGER.warn("打开文件夹失败", e);
        }
    }

    private void save() {
        Config.Data c = Config.get();
        try { c.http_port = Integer.parseInt(this.portField.getText().trim()); }
        catch (NumberFormatException e) { c.http_port = 25599; }
        c.http_host = this.hostField.getText().trim();
        if (c.http_host.isEmpty()) c.http_host = "127.0.0.1";

        String url = this.urlField.getText().trim();
        if (!url.isEmpty()) c.llm_base_url = url;
        c.llm_api_key = this.keyField.getText().trim();
        String model = this.modelField.getText().trim();
        if (!model.isEmpty()) c.llm_model = model;
        try { c.llm_decision_interval_sec = Double.parseDouble(this.intervalField.getText().trim()); }
        catch (NumberFormatException e) { c.llm_decision_interval_sec = 2.5; }

        Config.save();
        close();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double vy = mouseY + masterScroll;
        if (mouseX >= BOX_X && mouseX <= BOX_X + BOX_W &&
            vy >= Y_LIST_BOX && vy <= Y_LIST_BOX + LIST_H) {
            int row = (int) ((vy - Y_LIST_BOX) / ROW_H) + modelScroll;
            if (row >= 0 && row < modelNames.size()) selectedIndex = row;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double vy = mouseY + masterScroll;
        if (mouseX >= BOX_X && mouseX <= BOX_X + BOX_W &&
            vy >= Y_LIST_BOX && vy <= Y_LIST_BOX + LIST_H) {
            int visible = LIST_H / ROW_H;
            int maxM = Math.max(0, modelNames.size() - visible);
            modelScroll -= (int) Math.signum(verticalAmount);
            modelScroll = Math.max(0, Math.min(modelScroll, maxM));
            return true;
        }
        int maxScroll = Math.max(0, CONTENT_H - this.height + 10);
        masterScroll -= (int) Math.signum(verticalAmount);
        masterScroll = Math.max(0, Math.min(masterScroll, maxScroll));
        applyScroll();
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        int cx = this.width / 2;

        // 标题固定
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 4, 0xFFFFFF);

        // 裁剪
        context.enableScissor(0, 14, this.width, this.height - 2);

        int oy = -masterScroll;

        // 标签
        context.drawTextWithShadow(this.textRenderer, Text.literal("HTTP 主机:"), BOX_X, 22 + oy, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("HTTP 端口:"), BOX_X, 52 + oy, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("LLM 接口地址:"), BOX_X, 82 + oy, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("LLM API Key:"), BOX_X, 112 + oy, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("LLM 模型:"), BOX_X, 142 + oy, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("决策间隔(秒):"), BOX_X, 172 + oy, 0xAAAAAA);

        // 动作列表标题
        context.drawTextWithShadow(this.textRenderer,
            Text.literal("§e已加载动作 (" + modelNames.size() + ")"),
            BOX_X, 224 + oy, 0xFFFF55);

        // 子列表框
        int ly = Y_LIST_BOX + oy;
        context.fill(BOX_X, ly, BOX_X + BOX_W, ly + LIST_H, 0x80000000);
        context.drawBorder(BOX_X, ly, BOX_W, LIST_H, 0xFF555555);

        int visible = LIST_H / ROW_H;
        for (int i = 0; i < visible; i++) {
            int idx = i + modelScroll;
            if (idx >= modelNames.size()) break;
            int rowY = ly + i * ROW_H;
            boolean selected = (idx == selectedIndex);
            if (selected) context.fill(BOX_X + 1, rowY + 1, BOX_X + BOX_W - 1, rowY + ROW_H, 0x664488FF);
            context.drawTextWithShadow(this.textRenderer,
                Text.literal(" " + modelNames.get(idx)),
                BOX_X + 3, rowY + 2, selected ? 0xFFFFFF : 0xAAFFAA);
        }

        // 子列表滚动条
        if (modelNames.size() > visible) {
            int sbW = 6, sbX = BOX_X + BOX_W - sbW - 1;
            int maxM = modelNames.size() - visible;
            int trackH = LIST_H - 4;
            int thumbH = Math.max(10, trackH * visible / modelNames.size());
            int thumbY = ly + 2 + (maxM == 0 ? 0 : (trackH - thumbH) * modelScroll / maxM);
            context.fill(sbX, ly + 2, sbX + sbW, ly + LIST_H - 2, 0xFF333333);
            context.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xFF888888);
        }

        // 拖放区
        int dy = Y_DROP + oy;
        context.fill(BOX_X, dy, BOX_X + BOX_W, dy + 16, 0x66224422);
        context.drawBorder(BOX_X, dy, BOX_W, 16, 0xFF44AA44);
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§a点「打开文件夹」放入 .json，再点「刷新」"),
            BOX_X + BOX_W / 2, dy + 4, 0xFFFFFF);

        context.disableScissor();

        // 整页滚动条（屏幕右侧）
        int maxScroll = Math.max(0, CONTENT_H - this.height + 10);
        if (maxScroll > 0) {
            int sbW = 8;
            int sbX = this.width - sbW - 2;
            int trackY = 16;
            int trackH = this.height - 28;
            int thumbH = Math.max(20, trackH * this.height / CONTENT_H);
            int thumbY = trackY + (trackH - thumbH) * masterScroll / maxScroll;
            context.fill(sbX, trackY, sbX + sbW, trackY + trackH, 0xFF333333);
            context.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xFFAAAAAA);
        }

        // 渲染 widget
        super.render(context, mouseX, mouseY, delta);

        // 底部滚动提示
        if (maxScroll > 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§7滚轮滚动 " + masterScroll + "/" + maxScroll),
                cx, this.height - 10, 0x888888);
        }
    }

    @Override
    public void close() { this.client.setScreen(parent); }
}
