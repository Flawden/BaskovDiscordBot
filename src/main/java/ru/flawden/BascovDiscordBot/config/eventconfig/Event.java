package ru.flawden.BascovDiscordBot.config.eventconfig;

/**
 * Интерфейс Event определяет контракт для событий бота.
 * Все классы, реализующие этот интерфейс, должны предоставлять реализацию для выполнения события,
 * получения имени команды, помощи по использованию команды и проверки прав доступа.
 *
 * @author Flawden
 * @version 1.0
 */
public interface Event {

    /**
     * Выполняет команду события.
     *
     * @param event аргументы события, содержащие контекст выполнения
     */
    void execute(EventArgs event);

    /**
     * Возвращает имя группы команды, связанной с событием.
     *
     * @return имя группы
     */
    String getGroup();

    /**
     * Возвращает имя команды, связанной с событием.
     *
     * @return имя команды
     */
    String getName();

    /**
     * Возвращает сообщение помощи по команде.
     *
     * @return строка с сообщением помощи
     */
    String helpMessage();

    /**
     * Проверяет, требует ли команда прав создателя сервера для выполнения.
     *
     * @return true, если команда требует прав владельца; иначе false
     */
    boolean needOwner();
}

