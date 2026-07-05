package com.pixelmonspeedup.mixin;

import com.pixelmonmod.pixelmon.battles.controller.ai.MoveChoice;
import com.pixelmonmod.pixelmon.battles.controller.ai.type.AggressiveAI;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonspeedup.ai.AICache;
import com.pixelmonspeedup.ai.FastAIHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In Pixelmon 9.3.16, AggressiveAI.getNextMove only weights its own offensive moves (no opponent
 * simulation), so it is already cheap; replacing it with a random pick (the old fast path) would
 * change battle logic for no meaningful gain. Instead this mixin only adds a turn-scoped cache so
 * repeated getNextMove calls within the same turn are not recomputed.
 */
@SuppressWarnings("unused")
@Mixin(value = AggressiveAI.class, remap = false)
public abstract class AggressiveAIMixin {

    @Inject(method = "getNextMove", at = @At("HEAD"), cancellable = true)
    private void pixelmonSpeedup$serveCachedMove(PixelmonWrapper pw, CallbackInfoReturnable<MoveChoice> cir) {
        if (pw == null || pw.bc == null || pw.bc.simulateMode) return;
        if (!FastAIHelper.shouldFastPath(pw)) return;
        AICache.clearIfTurnAdvanced(pw, pw.bc.battleTurn);
        MoveChoice cached = AICache.getIfCurrent(pw, pw.bc.battleTurn);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getNextMove", at = @At("RETURN"))
    private void pixelmonSpeedup$cacheMove(PixelmonWrapper pw, CallbackInfoReturnable<MoveChoice> cir) {
        if (pw == null || pw.bc == null || pw.bc.simulateMode) return;
        if (!FastAIHelper.shouldFastPath(pw)) return;
        AICache.put(pw, pw.bc.battleTurn, cir.getReturnValue());
    }
}
