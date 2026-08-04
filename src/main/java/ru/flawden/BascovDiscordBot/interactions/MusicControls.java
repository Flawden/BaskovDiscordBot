package ru.flawden.BascovDiscordBot.interactions;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.List;

/**
 * Стабильные component id для музыкальных кнопок.
 */
public final class MusicControls {

    public static final String TOGGLE = "baskov:music:toggle";
    public static final String SKIP = "baskov:music:skip";
    public static final String STOP = "baskov:music:stop";
    public static final String QUEUE = "baskov:music:queue";
    public static final String REPEAT = "baskov:music:repeat";

    private MusicControls() {
    }

    public static List<ActionRow> rows() {
        return List.of(ActionRow.of(
                Button.primary(TOGGLE, "Пауза / играть"),
                Button.secondary(SKIP, "Пропустить"),
                Button.secondary(QUEUE, "Очередь"),
                Button.secondary(REPEAT, "Повтор"),
                Button.danger(STOP, "Стоп")
        ));
    }

    public static boolean supports(String componentId) {
        return TOGGLE.equals(componentId)
                || SKIP.equals(componentId)
                || STOP.equals(componentId)
                || QUEUE.equals(componentId)
                || REPEAT.equals(componentId);
    }
}
