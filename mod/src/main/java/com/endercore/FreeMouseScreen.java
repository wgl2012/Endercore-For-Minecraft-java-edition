package com.endercore;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 空白 Screen — 用于解放鼠标光标（不暂停游戏）
 */
public class FreeMouseScreen extends Screen {
    public FreeMouseScreen() {
        super(Text.literal("Free Mouse"));
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("🖱️ 鼠标已释放 - 按 P 键回到游戏"),
                this.width / 2, this.height / 2 - 10, 0x00ff00);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
