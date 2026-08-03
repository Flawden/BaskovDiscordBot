package ru.flawden.BascovDiscordBot.commands.music;

import net.dv8tion.jda.api.EmbedBuilder;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;

import java.awt.Color;

final class MusicCommandReply {

    private MusicCommandReply() {
    }

    static boolean allowOrReply(EventArgs event, MusicControlPolicy.Decision decision) {
        if (decision.allowed()) {
            return true;
        }

        event.getTextChannel().sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("🎧 Управление недоступно")
                        .setDescription(decision.message())
                        .setColor(Color.RED)
                        .build())
                .queue();
        return false;
    }
}
