package com.pixelmonspeedup.mixin;

import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.BattleStage;
import com.pixelmonmod.pixelmon.api.attackAnimations.AttackAnimation;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonspeedup.config.ConfigRuntime;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin callbacks (pixelmonSpeedup$...) are invoked by the Mixin runtime at injection points,
 * not by our code; static analysis may report them as unused, but they are required.
 */
@SuppressWarnings("unused")
@Mixin(BattleController.class)
public abstract class BattleControllerMixin {

    @Accessor("battleTicks")
    abstract int getBattleTicks();

    @Accessor("battleTicks")
    abstract void setBattleTicks(int value);

    @Accessor("turnList")
    abstract List<PixelmonWrapper> getTurnList();

    @Accessor("battleEnded")
    abstract boolean isBattleEnded();

    @Accessor("stage")
    abstract BattleStage getStage();

    @Invoker("doTurnLogic")
    abstract void invokeDoTurnLogic();

    @Accessor("actionIndex")
    abstract int getActionIndex();

    @Accessor
    abstract List<AttackAnimation> getCurrentAnimations();

    @Accessor
    abstract boolean isPaused();

    @Invoker("isEvolving")
    abstract boolean invokeIsEvolving();

    @Invoker("isFrozen")
    abstract boolean invokeIsFrozen();

    /**
     * Prevents the update loop from blocking turn logic while animations (e.g. health bar, move effects) are playing.
     * Animations still tick and play; the turn is allowed to proceed. When the next turn begins,
     * pixelmonSpeedup$clearAnimationsOnNewTurn clears any lingering animations so new ones start immediately.
     */
    /**
     * When the next mover is AI (non-player), return 21 so the 20-tick gate passes and doTurnLogic
     * runs on the next tick instead of waiting up to 20 ticks. Removes the "waiting for AI to decide" pause.
     */
    @Redirect(method = "update", at = @At(value = "FIELD", target = "Lcom/pixelmonmod/pixelmon/battles/controller/BattleController;battleTicks:I", opcode = org.objectweb.asm.Opcodes.GETFIELD), remap = false)
    private int pixelmonSpeedup$bypassTickGateForAI(BattleController self) {
        if (getStage() == BattleStage.DOACTION) {
            int idx = getActionIndex();
            List<PixelmonWrapper> turnList = getTurnList();
            if (idx >= 0 && turnList != null && idx < turnList.size()) {
                PixelmonWrapper next = turnList.get(idx);
                if (next != null && next.getParticipant() != null && !next.getParticipant().isPlayer()) {
                    return 21;
                }
            }
        }
        return getBattleTicks();
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"), remap = false)
    private boolean pixelmonSpeedup$allowTurnProceedDespiteAnimations(List<?> list) {
        return true;
    }

    /**
     * Re-implements the animation ticking logic with a crucial change:
     * It calls tickAnimation multiple times per tick, effectively speeding up the animation.
     * This replaces the old, flawed logic and correctly reduces animation wait times.
     */
    @Inject(method = "tickAnimations", at = @At("HEAD"), cancellable = true, remap = false)
    private void pixelmonSpeedup$speedUpAnimations(CallbackInfo ci) {
        ci.cancel(); // Prevent the original (empty) method from running

        List<AttackAnimation> currentAnimations = getCurrentAnimations();
        if (currentAnimations.isEmpty() || isPaused() || invokeIsEvolving() || invokeIsFrozen()) {
            return;
        }

        // SAFETY: Drop any animations whose user/target entities are missing (matches Pixelmon logic)
        currentAnimations.removeIf(animation -> {
            try {
                return animation == null || animation.user == null || animation.target == null ||
                    animation.user.getEntity() == null || animation.target.getEntity() == null;
            } catch (Throwable t) {
                return true;
            }
        });
        if (currentAnimations.isEmpty()) return;

        // Interrupt and queue-capping logic to prevent animation backlog
        if (ConfigRuntime.isInterruptAnimationsEnabled() && currentAnimations.size() > ConfigRuntime.getMaxAnimationQueue()) {
            int maxQueue = ConfigRuntime.getMaxAnimationQueue();
            while (currentAnimations.size() > maxQueue) {
                currentAnimations.remove(0);
            }
        }

        int speedMultiplier = ConfigRuntime.getAnimationSpeedMultiplier();
        if (speedMultiplier <= 1) {
            // Run original logic if speedup is disabled
            currentAnimations.removeIf(animation -> {
                // Mirror Pixelmon’s null checks again before ticking
                if (animation == null || animation.user == null || animation.target == null) return true;
                if (animation.user.getEntity() == null || animation.target.getEntity() == null) return true;
                return animation.tickAnimation(animation.ticks++);
            });
            return;
        }

        // The core speedup logic: process each animation multiple times.
        currentAnimations.removeIf(animation -> {
            boolean isFinished = false;
            for (int i = 0; i < speedMultiplier; i++) {
                // Check entity validity each substep to avoid NPEs
                if (animation == null || animation.user == null || animation.target == null) { isFinished = true; break; }
                if (animation.user.getEntity() == null || animation.target.getEntity() == null) { isFinished = true; break; }
                if (animation.tickAnimation(animation.ticks++)) {
                    isFinished = true;
                    break;
                }
            }
            return isFinished;
        });
    }

    /**
     * Reduce post-selection waiting by bypassing the 20-tick gate when safe.
     * Only bypasses during PICKACTION so that DOACTION (move execution) runs at most once per tick,
     * preserving battle message readability and correct priority-move order.
     * Never advances after battle has ended (e.g. successful flee).
     */
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lcom/pixelmonmod/pixelmon/battles/controller/BattleController;onUpdate()V", shift = At.Shift.AFTER), remap = false)
    private void pixelmonSpeedup$advanceLogicWhenReady(CallbackInfo ci) {
        BattleController self = (BattleController)(Object)this;
        if (isBattleEnded()) return;
        if (invokeIsFrozen() || isPaused() || invokeIsEvolving()) return;
        if (!getCurrentAnimations().isEmpty()) return;
        if (psu$interActionBufferTicks > 0) return;
        // Do not run extra doTurnLogic during DOACTION: one move per tick so "[pokemon] used [move]" messages
        // are readable and priority/speed order is respected (first striker goes first).
        if (getStage() == BattleStage.DOACTION) return;
        if (!self.isWaiting()) {
            boolean bypassGate = ConfigRuntime.isFastAdvanceFromPickEnabled() && getBattleTicks() < 20;
            if (bypassGate) {
                int mult = ConfigRuntime.getTurnStepMultiplier();
                if (mult < 1) mult = 1;
                for (int i = 0; i < mult && !self.isWaiting() && !isBattleEnded(); i++) {
                    invokeDoTurnLogic();
                }
            }
        }
    }

    /**
     * Insert a small 0.3s buffer (~6 ticks) between actions to improve legibility.
     * Implemented by briefly holding the action index at the current value for a few ticks
     * after an action completes and before advancing to the next.
     */
    private int psu$interActionBufferTicks = 0;
    private int psu$lastActionIndexObserved = -2;
    private boolean psu$appliedStartOfTurnBuffer = false;
    private boolean psu$appliedBattleStartBuffer = false;

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lcom/pixelmonmod/pixelmon/battles/controller/BattleController;doTurnLogic()V", shift = At.Shift.AFTER), remap = false)
    private void pixelmonSpeedup$applyInterActionBuffer(CallbackInfo ci) {
        // If an action just executed (actionIndex advanced or was set), start/continue a short buffer
        int idx = getActionIndex();
        if (idx != psu$lastActionIndexObserved) {
            psu$lastActionIndexObserved = idx;
            psu$interActionBufferTicks = Math.max(0, com.pixelmonspeedup.config.ConfigRuntime.getInterActionBufferTicks());
            if (com.pixelmonspeedup.config.ConfigRuntime.isDebugBattleBufferLogsEnabled()) {
                System.out.println("[PixelmonSpeedup] Buffer started after action; ticks=" + psu$interActionBufferTicks + " actionIndex=" + idx);
            }
            return;
        }
        if (psu$interActionBufferTicks > 0) {
            // Hold current action index to prevent immediate rapid-fire next step
            psu$interActionBufferTicks--;
            if (com.pixelmonspeedup.config.ConfigRuntime.isDebugBattleBufferLogsEnabled()) {
                System.out.println("[PixelmonSpeedup] Buffer blocking turn logic; ticksLeft=" + psu$interActionBufferTicks);
            }
            // No-op: Just by not calling next logic here, we maintain a tiny pause between actions
        }
    }

    /**
     * Apply buffer at battle start and at start of each new turn before any actions execute.
     * We detect battle start on first update after init; we detect start-of-turn when actionIndex resets to -1.
     */
    @Inject(method = "update", at = @At("HEAD"), remap = false)
    private void pixelmonSpeedup$startBuffers(CallbackInfo ci) {
        int buf = Math.max(0, com.pixelmonspeedup.config.ConfigRuntime.getInterActionBufferTicks());
        // Decrement any active buffer every update tick
        if (psu$interActionBufferTicks > 0) {
            psu$interActionBufferTicks--;
        }
        // Battle start: first HEAD after init and before any actions
        if (!psu$appliedBattleStartBuffer && getActionIndex() == -1) {
            psu$appliedBattleStartBuffer = true;
            psu$interActionBufferTicks = Math.max(psu$interActionBufferTicks, buf);
            if (com.pixelmonspeedup.config.ConfigRuntime.isDebugBattleBufferLogsEnabled()) {
                System.out.println("[PixelmonSpeedup] Battle start buffer applied; ticks=" + psu$interActionBufferTicks);
            }
        }
        // Start of turn: Pixelmon resets actionIndex to -1 before selecting/doing actions
        if (!psu$appliedStartOfTurnBuffer && getActionIndex() == -1) {
            psu$appliedStartOfTurnBuffer = true;
            psu$interActionBufferTicks = Math.max(psu$interActionBufferTicks, buf);
            if (com.pixelmonspeedup.config.ConfigRuntime.isDebugBattleBufferLogsEnabled()) {
                System.out.println("[PixelmonSpeedup] Start-of-turn buffer applied; ticks=" + psu$interActionBufferTicks);
            }
        }
        // Once actionIndex becomes >= 0 (in DOACTION), allow next cycle to apply at next turn
        if (getActionIndex() >= 0) {
            psu$appliedStartOfTurnBuffer = false;
        }
    }

    /**
     * Gate the built-in doTurnLogic call site inside update() to respect our buffer.
     * Do not run doTurnLogic after battle has ended (e.g. successful flee).
     * When the next mover is AI, bypass the buffer so we do not add a visible "waiting for AI" pause.
     */
    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lcom/pixelmonmod/pixelmon/battles/controller/BattleController;doTurnLogic()V"),
            remap = false)
    private void pixelmonSpeedup$gateDoTurnLogic(BattleController self) {
        if (isBattleEnded()) return;
        boolean nextMoverIsAI = pixelmonSpeedup$isCurrentMoverAI();
        if ((psu$interActionBufferTicks > 0 && !nextMoverIsAI) || invokeIsFrozen() || isPaused() || invokeIsEvolving()) {
            if (psu$interActionBufferTicks > 0 && !nextMoverIsAI && com.pixelmonspeedup.config.ConfigRuntime.isDebugBattleBufferLogsEnabled()) {
                System.out.println("[PixelmonSpeedup] doTurnLogic gated by buffer; ticksLeft=" + psu$interActionBufferTicks);
            }
            return;
        }
        self.doTurnLogic();
    }

    /** True when in DOACTION and the Pokemon at actionIndex belongs to a non-player (AI) participant. */
    private boolean pixelmonSpeedup$isCurrentMoverAI() {
        if (getStage() != BattleStage.DOACTION) return false;
        int idx = getActionIndex();
        List<PixelmonWrapper> turnList = getTurnList();
        if (idx < 0 || turnList == null || idx >= turnList.size()) return false;
        PixelmonWrapper pw = turnList.get(idx);
        return pw != null && pw.getParticipant() != null && !pw.getParticipant().isPlayer();
    }

    /**
     * At the start of a new turn (PICKACTION), clear any lingering animations so the next turn's
     * animations can begin immediately. Allows health/move animations to be skipped when the turn has already advanced.
     */
    @Inject(method = "doTurnLogic", at = @At("HEAD"), remap = false)
    private void pixelmonSpeedup$clearAnimationsOnNewTurn(CallbackInfo ci) {
        if (getStage() == BattleStage.PICKACTION) {
            List<AttackAnimation> anims = getCurrentAnimations();
            if (!anims.isEmpty()) {
                // Only remove broken/stale animations whose entity references are gone —
                // avoids abruptly dropping live animations that may hold native rendering
                // resources (PlayBattleParticleSystemPacket, particle handles) still in flight.
                anims.removeIf(anim -> {
                    try {
                        return anim == null || anim.user == null || anim.target == null
                            || anim.user.getEntity() == null || anim.target.getEntity() == null;
                    } catch (Throwable t) {
                        return true;
                    }
                });
            }
        }
    }

    /**
     * Hard-cancel doTurnLogic when buffer is active or battle has ended, regardless of invocation site.
     * Prevents further turn logic after e.g. successful flee. When the current mover is AI we do not
     * cancel for buffer so that the gate can allow AI moves through without a visible pause.
     */
    @Inject(method = "doTurnLogic", at = @At("HEAD"), cancellable = true, remap = false)
    private void pixelmonSpeedup$cancelDoTurnLogicIfBuffered(CallbackInfo ci) {
        if (isBattleEnded()) {
            ci.cancel();
            return;
        }
        if (psu$interActionBufferTicks > 0 && !pixelmonSpeedup$isCurrentMoverAI()) {
            if (com.pixelmonspeedup.config.ConfigRuntime.isDebugBattleBufferLogsEnabled()) {
                System.out.println("[PixelmonSpeedup] Cancelling doTurnLogic (direct) due to buffer; ticksLeft=" + psu$interActionBufferTicks);
            }
            ci.cancel();
        }
    }
}


