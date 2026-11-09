package com.endercore;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * 游戏内 AI 目标输入界面（按 O 键打开）
 */
public class GoalScreen extends Screen {
    private TextFieldWidget input;
    private static String lastGoal = "";

    public GoalScreen() {
        super(Text.literal("EnderCore AI"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        input = new TextFieldWidget(this.textRenderer, cx - 150, cy - 20, 300, 20, Text.literal("输入目标"));
        input.setMaxLength(200);
        input.setText(lastGoal);
        this.addDrawableChild(input);
        this.setFocused(input);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("发送目标"), btn -> {
            String goal = input.getText().trim();
            lastGoal = goal;
            AiHud.goal = goal;
            AiHud.setStatus("目标已设定");
            this.close();
        }).dimensions(cx - 150, cy + 10, 145, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("取消"), btn -> this.close())
                .dimensions(cx + 5, cy + 10, 145, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("⚡ EnderCore - 告诉 AI 要做什么"),
                this.width / 2, this.height / 2 - 50, 0x9d5cff);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
