package ru.flawden.BascovDiscordBot.interactions;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

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
                Commands.slash("status", "Показывает состояние Discord, музыки и команд"),
                Commands.slash("play", "Ищет и добавляет песню")
                        .addOptions(new OptionData(
                                OptionType.STRING,
                                "query",
                                "Название песни (поиск YouTube) или ссылка SoundCloud/YouTube",
                                true)
                                .setAutoComplete(true)),
                Commands.slash("search", "Показывает несколько результатов YouTube на выбор")
                        .addOptions(new OptionData(
                                OptionType.STRING,
                                "query",
                                "Название песни для поиска на YouTube",
                                true)
                                .setAutoComplete(true)),
                Commands.slash("history", "Показывает недавнюю историю воспроизведения")
                        .addOption(
                                OptionType.INTEGER,
                                "page",
                                "Страница истории, начиная с 1",
                                false),
                Commands.slash("replay", "Повторно добавляет трек из истории")
                        .addOption(
                                OptionType.INTEGER,
                                "position",
                                "Номер трека из /history, где 1 — самый новый",
                                true),
                Commands.slash("playlist", "Управляет постоянными плейлистами сервера")
                        .addSubcommands(
                                new SubcommandData("list", "Показывает плейлисты сервера"),
                                new SubcommandData("create", "Создаёт новый плейлист")
                                        .addOption(
                                                OptionType.STRING,
                                                "name",
                                                "Название плейлиста",
                                                true),
                                new SubcommandData("show", "Показывает содержимое плейлиста")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "name",
                                                "Название плейлиста",
                                                true)
                                                .setAutoComplete(true))
                                        .addOption(
                                                OptionType.INTEGER,
                                                "page",
                                                "Страница плейлиста, начиная с 1",
                                                false),
                                new SubcommandData("add", "Добавляет текущий трек в плейлист")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "name",
                                                "Название плейлиста",
                                                true)
                                                .setAutoComplete(true)),
                                new SubcommandData("play", "Добавляет весь плейлист в очередь")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "name",
                                                "Название плейлиста",
                                                true)
                                                .setAutoComplete(true)),
                                new SubcommandData("remove", "Удаляет трек из плейлиста")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "name",
                                                "Название плейлиста",
                                                true)
                                                .setAutoComplete(true))
                                        .addOption(
                                                OptionType.INTEGER,
                                                "position",
                                                "Позиция трека в плейлисте",
                                                true),
                                new SubcommandData("move", "Перемещает трек внутри плейлиста")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "name",
                                                "Название плейлиста",
                                                true)
                                                .setAutoComplete(true))
                                        .addOption(OptionType.INTEGER, "from", "Текущая позиция", true)
                                        .addOption(OptionType.INTEGER, "to", "Новая позиция", true),
                                new SubcommandData("rename", "Переименовывает плейлист")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "name",
                                                "Текущее название",
                                                true)
                                                .setAutoComplete(true))
                                        .addOption(OptionType.STRING, "new-name", "Новое название", true),
                                new SubcommandData("copy", "Создаёт твою копию плейлиста")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "name",
                                                "Исходный плейлист",
                                                true)
                                                .setAutoComplete(true))
                                        .addOption(OptionType.STRING, "new-name", "Название копии", true),
                                new SubcommandData("dedupe", "Удаляет повторные треки, сохраняя первую копию")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "name",
                                                "Название плейлиста",
                                                true)
                                                .setAutoComplete(true)),
                                new SubcommandData("capture-queue", "Сохраняет текущую музыкальную очередь в плейлист")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "name",
                                                "Название плейлиста",
                                                true)
                                                .setAutoComplete(true))
                                        .addOption(OptionType.BOOLEAN, "include-current", "Включить текущий трек", false),
                                new SubcommandData("add-history", "Добавляет трек из истории в плейлист")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "name",
                                                "Название плейлиста",
                                                true)
                                                .setAutoComplete(true))
                                        .addOption(OptionType.INTEGER, "position", "Позиция из /history", true),
                                new SubcommandData("search", "Ищет по названиям плейлистов, треков и исполнителей")
                                        .addOption(OptionType.STRING, "query", "Что искать в библиотеке", true),
                                new SubcommandData("delete", "Удаляет плейлист")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "name",
                                                "Название плейлиста",
                                                true)
                                                .setAutoComplete(true))),
                Commands.slash("pause", "Приостанавливает текущую песню"),
                Commands.slash("resume", "Продолжает воспроизведение"),
                Commands.slash("previous", "Возвращает предыдущую песню"),
                Commands.slash("skip", "Пропускает песню или голосует по правилам сервера"),
                Commands.slash("voteskip", "Голосует за пропуск текущей песни"),
                Commands.slash("stop", "Останавливает музыку и отключает бота"),
                Commands.slash("queue", "Показывает текущий трек и очередь")
                        .addOption(
                                OptionType.INTEGER,
                                "page",
                                "Страница очереди, начиная с 1",
                                false),
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
                Commands.slash("clear", "Очищает ожидающие треки, не останавливая текущий"),
                Commands.slash("queue-manage", "Расширенное управление ожидающей очередью")
                        .addSubcommands(
                                new SubcommandData("stats", "Показывает ревизию и сводку очереди"),
                                new SubcommandData("remove-range", "Удаляет непрерывный диапазон позиций")
                                        .addOption(OptionType.INTEGER, "start", "Первая позиция диапазона", true)
                                        .addOption(OptionType.INTEGER, "end", "Последняя позиция диапазона", true)
                                        .addOption(OptionType.INTEGER, "revision", "Ревизия из /queue; защищает от устаревших позиций", false),
                                new SubcommandData("dedupe", "Удаляет повторные копии ожидающих треков")
                                        .addOption(OptionType.INTEGER, "revision", "Ревизия из /queue; защищает от устаревших позиций", false),
                                new SubcommandData("remove-mine", "Удаляет только твои ожидающие треки")
                                        .addOption(OptionType.INTEGER, "revision", "Ревизия из /queue; защищает от устаревших позиций", false)),
                Commands.slash("settings", "Показывает и изменяет постоянные настройки сервера")
                        .addSubcommands(
                                new SubcommandData("show", "Показывает сохранённые настройки"),
                                new SubcommandData("volume", "Сохраняет громкость по умолчанию")
                                        .addOption(
                                                OptionType.INTEGER,
                                                "level",
                                                "Громкость для новых музыкальных сессий",
                                                true),
                                new SubcommandData("repeat", "Сохраняет режим повтора по умолчанию")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "mode",
                                                "Режим повтора для новых музыкальных сессий",
                                                true)
                                                .addChoice("Выключить", "off")
                                                .addChoice("Текущий трек", "track")
                                                .addChoice("Всю очередь", "queue")),
                                new SubcommandData("access", "Выбирает правила управления музыкой")
                                        .addOptions(new OptionData(
                                                OptionType.STRING,
                                                "mode",
                                                "Кто может напрямую управлять сессией",
                                                true)
                                                .addChoice("Открытый доступ", "open")
                                                .addChoice("Только DJ", "dj")
                                                .addChoice("DJ + голосование за пропуск", "vote")),
                                new SubcommandData("dj-role", "Назначает или очищает DJ-роль")
                                        .addOption(
                                                OptionType.ROLE,
                                                "role",
                                                "DJ-роль; оставь пустым, чтобы очистить",
                                                false),
                                new SubcommandData("vote-threshold", "Настраивает процент голосов для пропуска")
                                        .addOption(
                                                OptionType.INTEGER,
                                                "percent",
                                                "От 25 до 100 процентов слушателей",
                                                true),
                                new SubcommandData("reset", "Возвращает настройки сервера к значениям по умолчанию"))
        );
    }
}
