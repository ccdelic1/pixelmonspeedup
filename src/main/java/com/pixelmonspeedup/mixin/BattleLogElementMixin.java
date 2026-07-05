package com.pixelmonspeedup.mixin;

import com.pixelmonmod.pixelmon.client.gui.battles.battleScreens.BattleLogElement;
import com.pixelmonspeedup.config.ConfigRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Scales Pixelmon's battle log message auto-ack timer to reduce the post-turn "waiting" pause.
 *
 * The client counts down before auto-acknowledging each battle message; the client only sends
 * ParticipantReady after messages are acknowledged, so the server stays in ENDTURN until then.
 * In Pixelmon <= 9.3.8 this countdown was 80 ticks; as of 9.3.16 BattleLogElement.tick() resets
 * the counter to 5 ticks. We scale that base by the configured multiplier (floor of 1 tick) so
 * messages acknowledge even sooner while staying readable in the log.
 */
@Mixin(value = BattleLogElement.class, remap = false)
public abstract class BattleLogElementMixin {

    /** Base auto-ack countdown in Pixelmon 9.3.16 (BattleLogElement.tick sets activeCounter = 5). */
    private static final int BASE_TICKS = 5;

    @ModifyConstant(method = "tick", constant = @Constant(intValue = BASE_TICKS))
    private int pixelmonSpeedup$scaleBattleMessageTimer(int original) {
        double mult = ConfigRuntime.getBattleMessageSpeedMultiplier();
        if (mult < 0.01d || mult > 1.00d) {
            mult = 1.00d;
        }
        return Math.max(1, (int) Math.round(original * mult));
    }
}
