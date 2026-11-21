package com.endercore.mixin;

import com.endercore.HostMode;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 方案 B：后台托管期间强制游戏不暂停。
 *
 * 正常情况下打开暂停界面 / 失焦相关逻辑会让 {@link MinecraftClient#isPaused()}
 * 返回 true，进而暂停集成服务器与世界 tick。托管模式下在 HEAD 直接返回 false，
 * 保证 AI 控制循环在后台仍能持续推进。
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "isPaused", at = @At("HEAD"), cancellable = true)
    private void endercore$keepRunningInHostMode(CallbackInfoReturnable<Boolean> cir) {
        if (HostMode.active) {
            cir.setReturnValue(false);
        }
    }
}
