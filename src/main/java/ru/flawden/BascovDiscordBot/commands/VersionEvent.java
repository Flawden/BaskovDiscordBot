package ru.flawden.BascovDiscordBot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.eventconfig.Event;
import ru.flawden.BascovDiscordBot.config.eventconfig.EventArgs;

import java.awt.Color;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Показывает версию и основные метаданные запущенной сборки.
 */
@Component
public class VersionEvent implements Event {

    private static final DateTimeFormatter BUILD_TIME_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm 'UTC'")
            .withZone(ZoneId.of("UTC"));

    private final ObjectProvider<BuildProperties> buildPropertiesProvider;

    public VersionEvent(ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.buildPropertiesProvider = buildPropertiesProvider;
    }

    @Override
    public void execute(EventArgs event) {
        event.getTextChannel().sendMessageEmbeds(buildEmbed()).queue();
    }

    public MessageEmbed buildEmbed() {
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        String version = resolveVersion(buildProperties);
        String revision = resolveRevision(buildProperties);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎤 Baskov Discord Bot");
        embed.setDescription("Стабильная production-сборка музыкального бота.");
        embed.setColor(Color.CYAN);
        embed.addField("Версия", "`v" + version + "`", true);
        embed.addField("Java", "`" + System.getProperty("java.version") + "`", true);
        if (!"development".equals(revision)) {
            embed.addField("Commit", "`" + shortRevision(revision) + "`", true);
        }

        Optional.ofNullable(buildProperties)
                .map(BuildProperties::getTime)
                .ifPresent(buildTime -> embed.addField(
                        "Собрано",
                        "`" + BUILD_TIME_FORMAT.format(buildTime) + "`",
                        false));
        return embed.build();
    }

    String resolveVersion(BuildProperties buildProperties) {
        if (buildProperties == null || buildProperties.getVersion() == null
                || buildProperties.getVersion().isBlank()) {
            return "development";
        }
        return buildProperties.getVersion();
    }

    String resolveRevision(BuildProperties buildProperties) {
        if (buildProperties == null) {
            return "development";
        }
        String revision = buildProperties.get("revision");
        if (revision == null || revision.isBlank()) {
            return "development";
        }
        return revision.trim();
    }

    private static String shortRevision(String revision) {
        return revision.length() <= 12 ? revision : revision.substring(0, 12);
    }

    @Override
    public String getGroup() {
        return "Общие";
    }

    @Override
    public String getName() {
        return "version";
    }

    @Override
    public String helpMessage() {
        return "Показывает версию и метаданные текущей сборки";
    }

    @Override
    public boolean needOwner() {
        return false;
    }
}
