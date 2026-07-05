package com.pixelmonspeedup.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ConfigRuntime {
    // Performance and AI acceleration
    private static volatile int animationSpeed = 3; // 1..10
    private static volatile boolean aiFast = true;

    // Battle turn processing acceleration
    private static volatile int turnStepMultiplier = 3; // extra doTurnLogic steps per tick when safe
    private static volatile boolean fastAdvanceFromPick = true; // skip idle in PICKACTION when ready
    private static volatile double battleMessageSpeed = 0.3d;
    private static volatile int interActionBufferTicks = 4;

    // Animation behavior
    private static volatile boolean interruptAnimations = true; // allow new animation to preempt current
    private static volatile int maxAnimationQueue = 1; // cap queued animations to avoid backlog

    // Messages
    private static volatile boolean showWelcomeMessage = true;

    // Debug helpers
    private static volatile boolean debugBattleBufferLogs = false;

    // Section-prefixed configuration keys
    private static final String CAT_PERFORMANCE = "performance";
    private static final String CAT_BATTLE = "battle";
    private static final String CAT_ANIMATION = "animation";
    private static final String CAT_MESSAGES = "messages";
    private static final String CAT_DEBUG = "debug";

    private static final String KEY_ANIM_SPEED = CAT_PERFORMANCE + ".animationSpeed";
    private static final String KEY_AI_FAST = CAT_PERFORMANCE + ".aiFast";
    private static final String KEY_TURN_MULT = CAT_BATTLE + ".turnStepMultiplier";
    private static final String KEY_FAST_ADV_PICK = CAT_BATTLE + ".fastAdvanceFromPick";
    private static final String KEY_BATTLE_MSG_SPEED = CAT_BATTLE + ".battleMessageSpeed";
    private static final String KEY_INTERACTION_BUFFER_TICKS = CAT_BATTLE + ".interActionBufferTicks";
    private static final String KEY_ANIM_INTERRUPT = CAT_ANIMATION + ".interruptAnimations";
    private static final String KEY_ANIM_MAX_QUEUE = CAT_ANIMATION + ".maxAnimationQueue";
    private static final String KEY_SHOW_WELCOME_MESSAGE = CAT_MESSAGES + ".showWelcomeMessage";
    private static final String KEY_DEBUG_BUFFER_LOGS = CAT_DEBUG + ".debugBattleBufferLogs";

    private ConfigRuntime() {
        // utility class
    }

    public static void init() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("PixelmonSpeedup.toml");
        CommentedFileConfig cfg = CommentedFileConfig.builder(configPath)
                .sync()
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .build();
        cfg.load();

        // --- [performance] ---
        cfg.setComment(CAT_PERFORMANCE, "Performance and AI acceleration");

        cfg.setComment(KEY_ANIM_SPEED,
                " Multiplier applied to animation ticks (1=normal, higher is faster).\n" +
                " Default: 3\n" +
                " Range: 1 ~ 10");
        int anim = safeInt(cfg.get(KEY_ANIM_SPEED), animationSpeed);
        if (anim < 1) anim = 1;
        if (anim > 10) anim = 10;
        cfg.set(KEY_ANIM_SPEED, anim);

        cfg.setComment(KEY_AI_FAST,
                " When true, AI decisions may be skipped or cached to reduce wait times.\n" +
                " Default: true");
        boolean aif = cfg.getOrElse(KEY_AI_FAST, aiFast);
        cfg.set(KEY_AI_FAST, aif);

        // --- [battle] ---
        cfg.setComment(CAT_BATTLE, "Battle turn processing acceleration");

        cfg.setComment(KEY_TURN_MULT,
                " Extra doTurnLogic steps per tick when safe.\n" +
                " Default: 3\n" +
                " Range: 1 ~ 10");
        int turnMult = safeInt(cfg.get(KEY_TURN_MULT), turnStepMultiplier);
        if (turnMult < 1) turnMult = 1;
        cfg.set(KEY_TURN_MULT, turnMult);

        cfg.setComment(KEY_FAST_ADV_PICK,
                " Skip idle in PICKACTION when all inputs are ready.\n" +
                " Default: true");
        boolean fastPick = cfg.getOrElse(KEY_FAST_ADV_PICK, fastAdvanceFromPick);
        cfg.set(KEY_FAST_ADV_PICK, fastPick);

        cfg.setComment(KEY_BATTLE_MSG_SPEED,
                " Speed multiplier for battle messages (e.g. \"[pokemon] used [move]!\").\n" +
                " Lower = faster. Values outside the range are ignored and treated as 1.00.\n" +
                " Default: 0.3\n" +
                " Range: 0.01 ~ 1.00");
        double msgSpeed = safeDouble(cfg.get(KEY_BATTLE_MSG_SPEED), battleMessageSpeed);
        if (msgSpeed < 0.01d || msgSpeed > 1.00d) {
            msgSpeed = 1.00d;
        }
        cfg.set(KEY_BATTLE_MSG_SPEED, msgSpeed);

        cfg.setComment(KEY_INTERACTION_BUFFER_TICKS,
                " Buffer ticks between sequential actions to improve legibility (~0.05s per tick).\n" +
                " Default: 4\n" +
                " Range: 0 ~ 40");
        int interBuf = safeInt(cfg.get(KEY_INTERACTION_BUFFER_TICKS), interActionBufferTicks);
        if (interBuf < 0) interBuf = 0;
        if (interBuf > 40) interBuf = 40;
        cfg.set(KEY_INTERACTION_BUFFER_TICKS, interBuf);

        // --- [animation] ---
        cfg.setComment(CAT_ANIMATION, "Animation behavior");

        cfg.setComment(KEY_ANIM_INTERRUPT,
                " Allow new animation to interrupt existing one. Reduces backlog.\n" +
                " Default: true");
        boolean animInterrupt = cfg.getOrElse(KEY_ANIM_INTERRUPT, interruptAnimations);
        cfg.set(KEY_ANIM_INTERRUPT, animInterrupt);

        cfg.setComment(KEY_ANIM_MAX_QUEUE,
                " Maximum number of animations to keep queued. Lower values reduce waiting.\n" +
                " Default: 1\n" +
                " Range: 1 ~ 10");
        int animMaxQueue = safeInt(cfg.get(KEY_ANIM_MAX_QUEUE), maxAnimationQueue);
        if (animMaxQueue < 1) animMaxQueue = 1;
        if (animMaxQueue > 10) animMaxQueue = 10;
        cfg.set(KEY_ANIM_MAX_QUEUE, animMaxQueue);

        // --- [messages] ---
        cfg.setComment(CAT_MESSAGES, "Welcome message shown when a player loads into the world");

        cfg.setComment(KEY_SHOW_WELCOME_MESSAGE,
                " When true, the welcome chat message is shown when a player loads into the world.\n" +
                " When false, this message is disabled.\n" +
                " Default: true");
        boolean showWelcome = cfg.getOrElse(KEY_SHOW_WELCOME_MESSAGE, showWelcomeMessage);
        cfg.set(KEY_SHOW_WELCOME_MESSAGE, showWelcome);

        // --- [debug] ---
        cfg.setComment(CAT_DEBUG, "Debug helpers");

        cfg.setComment(KEY_DEBUG_BUFFER_LOGS,
                " Log when the inter-action buffer is active.\n" +
                " Default: false");
        boolean debugBuffer = cfg.getOrElse(KEY_DEBUG_BUFFER_LOGS, debugBattleBufferLogs);
        cfg.set(KEY_DEBUG_BUFFER_LOGS, debugBuffer);

        cfg.save();
        cfg.close();

        animationSpeed = anim;
        aiFast = aif;
        turnStepMultiplier = turnMult;
        fastAdvanceFromPick = fastPick;
        battleMessageSpeed = msgSpeed;
        interActionBufferTicks = interBuf;
        interruptAnimations = animInterrupt;
        maxAnimationQueue = animMaxQueue;
        showWelcomeMessage = showWelcome;
        debugBattleBufferLogs = debugBuffer;
    }

    // getters
    public static int getAnimationSpeedMultiplier() { return animationSpeed; }
    public static boolean isAIFastEnabled() { return aiFast; }
    public static int getTurnStepMultiplier() { return turnStepMultiplier; }
    public static boolean isFastAdvanceFromPickEnabled() { return fastAdvanceFromPick; }
    public static boolean isInterruptAnimationsEnabled() { return interruptAnimations; }
    public static int getMaxAnimationQueue() { return maxAnimationQueue; }
    public static int getInterActionBufferTicks() { return interActionBufferTicks; }
    public static boolean isDebugBattleBufferLogsEnabled() { return debugBattleBufferLogs; }
    public static boolean isShowWelcomeMessageEnabled() { return showWelcomeMessage; }
    public static double getBattleMessageSpeedMultiplier() { return battleMessageSpeed; }

    // helpers
    private static int safeInt(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        try { return v == null ? def : Integer.parseInt(String.valueOf(v)); } catch (Exception ex) { return def; }
    }

    private static double safeDouble(Object v, double def) {
        if (v instanceof Number n) return n.doubleValue();
        try { return v == null ? def : Double.parseDouble(String.valueOf(v)); } catch (Exception ex) { return def; }
    }
}
