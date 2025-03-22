package ru.flawden.BascovDiscordBot.commands.music;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.awt.*;
import java.io.IOException;
import java.net.URL;

@Component
public class SearchEvent implements Event {

    @Override
    public void execute(EventArgs event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        if (event.getArgs().length <= 1) {
            embed.setTitle("🔍 Ошибка поиска");
            embed.setDescription("Введите название песни через пробел, например: `!search Sabaton Heart of Iron`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String query = String.join(" ", event.getArgs()).substring(event.getArgs()[0].length() + 1);
        if (!isUrl(query)) {
            query = "scsearch:" + query;
        }

        if (!event.getMemberVoiceState().inAudioChannel()) {
            embed.setTitle("🔍 Ошибка подключения");
            embed.setDescription("Для начала нужно войти в голосовой чат!");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        if (event.getSelfVoiceState().inAudioChannel() &&
                !event.getSelfVoiceState().getChannel().equals(event.getMemberVoiceState().getChannel())) {
            embed.setTitle("🔍 Ошибка подключения");
            embed.setDescription("Я уже подключён к другому голосовому каналу: `" +
                    event.getSelfVoiceState().getChannel().getName() + "`");
            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        if (!event.getSelfVoiceState().inAudioChannel()) {
            final AudioManager audioManager = event.getGuild().getAudioManager();
            final VoiceChannel memberChannel = (VoiceChannel) event.getMemberVoiceState().getChannel();

            audioManager.openAudioConnection(memberChannel);

            GuildMusicManager guildMusicManager = PlayerManager.getINSTANCE().getMusicManager(event.getGuild());
            audioManager.setSendingHandler(guildMusicManager.getSendHandler());
        }

        PlayerManager.getINSTANCE().loadAndPlay(event.getTextChannel(), query);
    }

    private boolean isUrl(String link) {
        try {
            new URL(link).openStream().close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getGroup() {
        return "Музыка";
    }

    @Override
    public String getName() {
        return "search";
    }

    @Override
    public String helpMessage() {
        return "Воспроизводит песню по её названию (Например: \"!search Sabaton Heart of Iron)\")";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}