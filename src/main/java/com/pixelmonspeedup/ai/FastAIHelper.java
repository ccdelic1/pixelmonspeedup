package com.pixelmonspeedup.ai;

import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonspeedup.config.ConfigRuntime;

/**
 * Shared gate for the AI fast-path mixins: fast mode is active when enabled in config,
 * or when any player in the battle is waiting on the AI to decide.
 */
public final class FastAIHelper {

    private FastAIHelper() {}

    public static boolean shouldFastPath(PixelmonWrapper pw) {
        if (pw == null || pw.bc == null) return false;
        if (ConfigRuntime.isAIFastEnabled()) return true;
        BattleController bc = pw.bc;
        // Iterate participants directly to avoid bc.getPlayers() allocating a new ArrayList every call
        for (BattleParticipant p : bc.participants) {
            if (p.isPlayer() && ((PlayerParticipant)p).waiting()) return true;
        }
        return false;
    }
}
