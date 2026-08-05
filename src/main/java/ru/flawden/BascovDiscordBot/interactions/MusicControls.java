package ru.flawden.BascovDiscordBot.interactions;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Стабильные component id для музыкальных кнопок.
 */
public final class MusicControls {

    public static final String TOGGLE = "baskov:music:toggle";
    public static final String PREVIOUS = "baskov:music:previous";
    public static final String SKIP = "baskov:music:skip";
    public static final String STOP = "baskov:music:stop";
    public static final String QUEUE = "baskov:music:queue";
    public static final String REPEAT = "baskov:music:repeat";
    public static final String SHUFFLE = "baskov:music:shuffle";
    public static final String SEEK_BACKWARD = "baskov:music:seek:-15";
    public static final String SEEK_FORWARD = "baskov:music:seek:+15";
    private static final String QUEUE_PAGE_PREFIX = "baskov:queue:page:";
    private static final String SEARCH_PICK_PREFIX = "baskov:search:pick:";
    private static final String SEARCH_CANCEL_PREFIX = "baskov:search:cancel:";

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

    /**
     * Расширенный пульт для /now: навигация по истории, короткий seek и
     * операции над очередью без повторного ввода slash-команд.
     */
    public static List<ActionRow> nowRows() {
        return List.of(
                ActionRow.of(
                        Button.secondary(PREVIOUS, "⏮ Предыдущий"),
                        Button.secondary(SEEK_BACKWARD, "−15 сек"),
                        Button.primary(TOGGLE, "Пауза / играть"),
                        Button.secondary(SEEK_FORWARD, "+15 сек"),
                        Button.secondary(SKIP, "Следующий ⏭")),
                ActionRow.of(
                        Button.secondary(QUEUE, "Очередь"),
                        Button.secondary(SHUFFLE, "Перемешать"),
                        Button.secondary(REPEAT, "Повтор"),
                        Button.danger(STOP, "Стоп")));
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

    public static List<ActionRow> searchRows(String token, int candidateCount) {
        if (!validSearchToken(token)) {
            throw new IllegalArgumentException("Invalid search token");
        }
        if (candidateCount < 1 || candidateCount > SearchSelectionStore.MAX_CANDIDATES) {
            throw new IllegalArgumentException("Candidate count must be between 1 and 5");
        }

        List<Button> choices = new ArrayList<>();
        for (int index = 1; index <= candidateCount; index++) {
            choices.add(Button.primary(searchPickId(token, index), String.valueOf(index)));
        }
        return List.of(
                ActionRow.of(choices),
                ActionRow.of(Button.danger(searchCancelId(token), "Отмена")));
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

    public static Optional<SearchAction> searchAction(String componentId) {
        if (componentId == null) {
            return Optional.empty();
        }
        if (componentId.startsWith(SEARCH_CANCEL_PREFIX)) {
            String token = componentId.substring(SEARCH_CANCEL_PREFIX.length());
            return validSearchToken(token)
                    ? Optional.of(new SearchAction(SearchActionType.CANCEL, token, 0))
                    : Optional.empty();
        }
        if (!componentId.startsWith(SEARCH_PICK_PREFIX)) {
            return Optional.empty();
        }

        String payload = componentId.substring(SEARCH_PICK_PREFIX.length());
        int separator = payload.lastIndexOf(':');
        if (separator < 1 || separator == payload.length() - 1) {
            return Optional.empty();
        }
        String token = payload.substring(0, separator);
        if (!validSearchToken(token)) {
            return Optional.empty();
        }
        try {
            int index = Integer.parseInt(payload.substring(separator + 1));
            if (index < 1 || index > SearchSelectionStore.MAX_CANDIDATES) {
                return Optional.empty();
            }
            return Optional.of(new SearchAction(SearchActionType.PICK, token, index));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String searchPickId(String token, int index) {
        return SEARCH_PICK_PREFIX + token + ':' + index;
    }

    private static String searchCancelId(String token) {
        return SEARCH_CANCEL_PREFIX + token;
    }

    private static boolean validSearchToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{8,32}");
    }

    public static boolean supports(String componentId) {
        return TOGGLE.equals(componentId)
                || PREVIOUS.equals(componentId)
                || SKIP.equals(componentId)
                || STOP.equals(componentId)
                || QUEUE.equals(componentId)
                || REPEAT.equals(componentId)
                || SHUFFLE.equals(componentId)
                || SEEK_BACKWARD.equals(componentId)
                || SEEK_FORWARD.equals(componentId)
                || queuePage(componentId).isPresent()
                || searchAction(componentId).isPresent();
    }

    public enum SearchActionType {
        PICK,
        CANCEL
    }

    public record SearchAction(SearchActionType type, String token, int oneBasedIndex) {
    }
}
