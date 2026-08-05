package ru.flawden.BascovDiscordBot.interactions;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.List;
import java.util.OptionalInt;

/**
 * Стабильные component id для музыкальных кнопок.
 */
public final class MusicControls {

    public static final String TOGGLE = "baskov:music:toggle";
    public static final String SKIP = "baskov:music:skip";
    public static final String STOP = "baskov:music:stop";
    public static final String QUEUE = "baskov:music:queue";
    public static final String REPEAT = "baskov:music:repeat";
    private static final String QUEUE_PAGE_PREFIX = "baskov:queue:page:";

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

    public static List<ActionRow> queueRows(int page, int totalPages) {
        List<ActionRow> controls = rows();
        boolean hasPrevious = page > 1;
        boolean hasNext = page < totalPages;
        if (!hasPrevious && !hasNext) {
            return controls;
        }

        ActionRow navigation;
        if (hasPrevious && hasNext) {
            navigation = ActionRow.of(
                    Button.secondary(queuePageId(page - 1), "◀ Назад"),
                    Button.secondary(queuePageId(page + 1), "Вперёд ▶"));
        } else if (hasPrevious) {
            navigation = ActionRow.of(
                    Button.secondary(queuePageId(page - 1), "◀ Назад"));
        } else {
            navigation = ActionRow.of(
                    Button.secondary(queuePageId(page + 1), "Вперёд ▶"));
        }
        return List.of(controls.get(0), navigation);
    }

    public static String queuePageId(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("Queue page must be positive");
        }
        return QUEUE_PAGE_PREFIX + page;
    }

    public static OptionalInt queuePage(String componentId) {
        if (componentId == null || !componentId.startsWith(QUEUE_PAGE_PREFIX)) {
            return OptionalInt.empty();
        }
        String rawPage = componentId.substring(QUEUE_PAGE_PREFIX.length());
        try {
            int page = Integer.parseInt(rawPage);
            return page < 1 ? OptionalInt.empty() : OptionalInt.of(page);
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    public static boolean supports(String componentId) {
        return TOGGLE.equals(componentId)
                || SKIP.equals(componentId)
                || STOP.equals(componentId)
                || QUEUE.equals(componentId)
                || REPEAT.equals(componentId)
                || queuePage(componentId).isPresent();
    }
}
