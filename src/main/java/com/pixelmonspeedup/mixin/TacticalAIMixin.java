package com.pixelmonspeedup.mixin;

import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.ai.MoveChoice;
import com.pixelmonmod.pixelmon.battles.controller.ai.type.AggressiveAI;
import com.pixelmonmod.pixelmon.battles.controller.ai.type.TacticalAI;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonspeedup.ai.AICache;
import com.pixelmonspeedup.ai.FastAIHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Accelerates TacticalAI (Pixelmon 9.3.16, battles.controller.ai.type) by skipping the expensive
 * getWeightedAttackChoices() simulation (opponent move simulation, status move simulation) and
 * instead picking directly among the raw attack choices, the same way earlier fast mode did.
 *
 * Extends AggressiveAI (the target's superclass) so inherited protected members such as
 * getAttackChoicesOpponentOnly are directly accessible without reflection.
 */
@SuppressWarnings("unused")
@Mixin(value = TacticalAI.class, remap = false)
public abstract class TacticalAIMixin extends AggressiveAI {

    @Shadow
    protected abstract List<MoveChoice> getBestChoices(List<MoveChoice> choices);

    @Shadow
    protected abstract MoveChoice pickBestChoice(BattleController controller, List<MoveChoice> choices, List<MoveChoice> bestChoices);

    @Inject(method = "getNextMove", at = @At("HEAD"), cancellable = true)
    private void pixelmonSpeedup$fastNextMove(PixelmonWrapper pw, CallbackInfoReturnable<MoveChoice> cir) {
        try {
            if (!FastAIHelper.shouldFastPath(pw)) return;
            BattleController bc = pw.bc;
            boolean simulating = bc.simulateMode;

            if (!simulating) {
                AICache.clearIfTurnAdvanced(pw, bc.battleTurn);
                MoveChoice cached = AICache.getIfCurrent(pw, bc.battleTurn);
                if (cached != null) {
                    cir.setReturnValue(cached);
                    return;
                }
            }

            List<MoveChoice> choices = this.getAttackChoicesOpponentOnly(pw);
            if (choices == null || choices.isEmpty()) return;

            MoveChoice result = this.pickBestChoice(bc, choices, this.getBestChoices(choices));
            if (result == null) return;

            if (!simulating) {
                AICache.put(pw, bc.battleTurn, result);
            }
            cir.setReturnValue(result);
        } catch (Throwable ignored) {
            // Fall through to vanilla logic on any failure
        }
    }
}
