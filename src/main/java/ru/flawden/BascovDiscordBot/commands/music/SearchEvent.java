package ru.flawden.BascovDiscordBot.commands.music;

import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

@Component
public class SearchEvent implements Event {

    private EventArgs event;

    @Override
    public void execute(EventArgs event) {
        this.event = event;
        if (!isAnyArgs()) {
            return;
        }
        ;
        String link = isUrl(event.getArgs()[1]);
        if (!isInAudioChannel()) {
            return;
        }
        initAudioChannel();
        PlayerManager.getINSTANCE().loadAndPlay(event.getTextChannel(), link);
    }

    private String isUrl(String link) {
        try {
            new URL(link).openStream().close();
        } catch (IOException e) {
            link = Arrays.toString(event.getArgs());
            link = link.substring(link.indexOf(" "));
            link = "scsearch:" + link + " audio";
        } finally {
            return link;
        }
    }

    private boolean isAnyArgs() {
        boolean isAnyARGS = true;
        if (event.getArgs().length <= 1) {
            isAnyARGS = false;
            event.getTextChannel().sendMessage("Введите название песни через пробел - \"!search Название песни\"").queue();
        }
        return isAnyARGS;
    }

    private boolean isInAudioChannel() {
        if (!event.getMemberVoiceState().inAudioChannel()) {
            event.getTextChannel().sendMessage("Для начало нужно войти в голосовой чат").queue();
            return false;
        }
        return true;
    }

    private void initAudioChannel() {
        if (!event.getSelfVoiceState().inAudioChannel()) {
            final AudioManager audioManager = event.getGuild().getAudioManager();
            final VoiceChannel memberChannel = (VoiceChannel) event.getMemberVoiceState().getChannel();

            audioManager.openAudioConnection(memberChannel);
        }
    }

    @Override
    public String getName() {
        return "search";
    }

    @Override
    public String helpMessage() {
        return "Воспроизводит песню по её названию (Например: \"!Search Sabaton hearth of iron)\"";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
