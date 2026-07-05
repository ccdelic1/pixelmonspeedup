package com.pixelmonspeedup.ai;

import com.pixelmonmod.pixelmon.battles.controller.ai.MoveChoice;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;

import java.util.Map;
import java.util.WeakHashMap;

public final class AICache {
    private static final Map<PixelmonWrapper, Entry> CACHE = new WeakHashMap<>();

    private AICache() {}

    public static synchronized void put(PixelmonWrapper wrapper, int battleTurn, MoveChoice choice) {
        if (wrapper == null || choice == null) return;
        CACHE.put(wrapper, new Entry(battleTurn, choice));
    }

    public static synchronized MoveChoice getIfCurrent(PixelmonWrapper wrapper, int battleTurn) {
        Entry e = CACHE.get(wrapper);
        if (e == null) return null;
        if (e.turn != battleTurn) return null;
        return e.choice;
    }

    public static synchronized void clearIfTurnAdvanced(PixelmonWrapper wrapper, int battleTurn) {
        Entry e = CACHE.get(wrapper);
        if (e != null && e.turn != battleTurn) CACHE.remove(wrapper);
    }

    private static final class Entry {
        final int turn;
        final MoveChoice choice;
        Entry(int t, MoveChoice c) { this.turn = t; this.choice = c; }
    }
}


