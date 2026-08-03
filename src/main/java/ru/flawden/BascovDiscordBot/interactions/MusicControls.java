package ru.flawden.BascovDiscordBot.interactions;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.List;

/**
 * Стабильные component id для музыкальных кнопок.
 */
public final class MusicControls {

    public static final String TOGGLE = "baskov:music:toggle";
    public static final String SKIP = "baskov:music:skip";
    public static final String STOP = "baskov:music:stop";
    public static final String QUEUE = "baskov:music:queue";

    private MusicControls() {
    }

    public static List<LayoutComponent> rows() {
        return List.of(ActionRow.of(
                Button.primary(TOGGLE, "Пауза / играть"),
                Button.secondary(SKIP, "Пропустить"),
                Button.secondary(QUEUE, "Очередь"),
                Button.danger(STOP, "Стоп")
        ));
    }

    public static boolean supports(String componentId) {
        return TOGGLE.equals(componentId)
                || SKIP.equals(componentId)
                || STOP.equals(componentId)
                || QUEUE.equals(componentId);
    }
}
