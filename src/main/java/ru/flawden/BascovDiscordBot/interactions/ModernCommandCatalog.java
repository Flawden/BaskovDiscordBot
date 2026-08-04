package ru.flawden.BascovDiscordBot.interactions;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;

/**
 * Единый каталог глобальных slash-команд.
 */
public final class ModernCommandCatalog {

    private ModernCommandCatalog() {
    }

    public static List<CommandData> commands() {
        return List.of(
                Commands.slash("help", "Показывает современные команды Баскова"),
                Commands.slash("version", "Показывает версию запущенного бота"),
                Commands.slash("play", "Ищет и добавляет песню")
                        .addOptions(new OptionData(
                                OptionType.STRING,
                                "query",
                                "Название песни или ссылка SoundCloud/YouTube",
                                true)
                                .setAutoComplete(true)),
                Commands.slash("pause", "Приостанавливает текущую песню"),
                Commands.slash("resume", "Продолжает воспроизведение"),
                Commands.slash("skip", "Пропускает текущую песню"),
                Commands.slash("stop", "Останавливает музыку и отключает бота"),
                Commands.slash("queue", "Показывает текущий трек и очередь"),
                Commands.slash("now", "Показывает текущую песню"),
                Commands.slash("seek", "Перематывает текущую песню")
                        .addOption(
                                OptionType.STRING,
                                "position",
                                "Позиция: SS, MM:SS или HH:MM:SS",
                                true),
                Commands.slash("volume", "Изменяет громкость музыкальной сессии")
                        .addOption(
                                OptionType.INTEGER,
                                "level",
                                "Громкость от 0 до настроенного максимума",
                                true),
                Commands.slash("repeat", "Выбирает режим повтора")
                        .addOptions(new OptionData(
                                OptionType.STRING,
                                "mode",
                                "Что повторять",
                                true)
                                .addChoice("Выключить", "off")
                                .addChoice("Текущий трек", "track")
                                .addChoice("Всю очередь", "queue")),
                Commands.slash("shuffle", "Перемешивает ожидающие треки"),
                Commands.slash("remove", "Удаляет трек из очереди")
                        .addOption(
                                OptionType.INTEGER,
                                "position",
                                "Номер трека в /queue",
                                true),
                Commands.slash("move", "Перемещает трек внутри очереди")
                        .addOption(OptionType.INTEGER, "from", "Текущая позиция", true)
                        .addOption(OptionType.INTEGER, "to", "Новая позиция", true),
                Commands.slash("clear", "Очищает ожидающие треки, не останавливая текущий")
        );
    }
}
