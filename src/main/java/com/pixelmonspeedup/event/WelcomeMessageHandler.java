package com.pixelmonspeedup.event;

import com.pixelmonspeedup.config.ConfigRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Sends a single formatted welcome chat message to the player when they load into the world,
 * if the config option to show the welcome message is enabled.
 */
public final class WelcomeMessageHandler {

    private static final Style BOLD_YELLOW = Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true);
    private static final Style GRAY = Style.EMPTY.withColor(ChatFormatting.GRAY);

    private WelcomeMessageHandler() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ConfigRuntime.isShowWelcomeMessageEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Component message = buildWelcomeMessage();
        serverPlayer.sendSystemMessage(message, false);
    }

    private static Component buildWelcomeMessage() {
        MutableComponent line1 = Component.literal("Welcome to Pixelmon Speedup by CCDelic!\n\n")
                .withStyle(BOLD_YELLOW);
        MutableComponent line2 = Component.literal("You should check the config for battle speed and performance tweaks. You can also disable this message there!")
                .withStyle(GRAY);
        return Component.empty().append(line1).append(line2);
    }
}
