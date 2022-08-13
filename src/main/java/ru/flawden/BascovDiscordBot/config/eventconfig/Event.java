package ru.flawden.BascovDiscordBot.config.eventconfig;

public interface Event {
    void execute(EventArgs event);

    String getName();

    String helpMessage();

    boolean needOwner();
}

