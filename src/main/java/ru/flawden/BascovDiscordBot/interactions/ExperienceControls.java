package ru.flawden.BascovDiscordBot.interactions;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Component IDs для Discord UX: контекстная помощь, refresh статуса и подтверждения.
 */
public final class ExperienceControls {

    public static final String STATUS_REFRESH = "baskov:ux:status:refresh";
    private static final String HELP_PREFIX = "baskov:ux:help:";
    private static final String CONFIRM_PREFIX = "baskov:ux:confirm:";

    private ExperienceControls() {
    }

    public static List<ActionRow> helpRows(HelpSection active) {
        HelpSection selected = active == null ? HelpSection.OVERVIEW : active;
        return List.of(ActionRow.of(
                helpButton(HelpSection.OVERVIEW, selected, "Обзор"),
                helpButton(HelpSection.PLAYBACK, selected, "Музыка"),
                helpButton(HelpSection.QUEUE, selected, "Очередь"),
                helpButton(HelpSection.LIBRARY, selected, "Библиотека"),
                helpButton(HelpSection.ADMIN, selected, "Админ")
        ));
    }

    public static List<ActionRow> statusRows() {
        return List.of(ActionRow.of(Button.secondary(STATUS_REFRESH, "↻ Обновить статус")));
    }

    public static List<ActionRow> confirmationRows(String token) {
        validateToken(token);
        return List.of(ActionRow.of(
                Button.danger(confirmId(token, Decision.CONFIRM), "Подтвердить"),
                Button.secondary(confirmId(token, Decision.CANCEL), "Отмена")
        ));
    }

    public static Optional<HelpSection> helpSection(String componentId) {
        if (componentId == null || !componentId.startsWith(HELP_PREFIX)) {
            return Optional.empty();
        }
        return HelpSection.parse(componentId.substring(HELP_PREFIX.length()));
    }

    public static Optional<ConfirmationAction> confirmationAction(String componentId) {
        if (componentId == null || !componentId.startsWith(CONFIRM_PREFIX)) {
            return Optional.empty();
        }
        String payload = componentId.substring(CONFIRM_PREFIX.length());
        int separator = payload.lastIndexOf(':');
        if (separator < 1 || separator == payload.length() - 1) {
            return Optional.empty();
        }
        String token = payload.substring(0, separator);
        if (!validToken(token)) {
            return Optional.empty();
        }
        String rawDecision = payload.substring(separator + 1);
        Decision decision = switch (rawDecision) {
            case "yes" -> Decision.CONFIRM;
            case "no" -> Decision.CANCEL;
            default -> null;
        };
        return decision == null
                ? Optional.empty()
                : Optional.of(new ConfirmationAction(token, decision));
    }

    public static boolean supports(String componentId) {
        return STATUS_REFRESH.equals(componentId)
                || helpSection(componentId).isPresent()
                || confirmationAction(componentId).isPresent();
    }

    private static Button helpButton(
            HelpSection section,
            HelpSection selected,
            String label) {
        Button button = section == selected
                ? Button.primary(helpId(section), label)
                : Button.secondary(helpId(section), label);
        return section == selected ? button.asDisabled() : button;
    }

    private static String helpId(HelpSection section) {
        return HELP_PREFIX + section.id();
    }

    private static String confirmId(String token, Decision decision) {
        return CONFIRM_PREFIX + token + ':' + (decision == Decision.CONFIRM ? "yes" : "no");
    }

    private static void validateToken(String token) {
        if (!validToken(token)) {
            throw new IllegalArgumentException("Invalid confirmation token");
        }
    }

    private static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{12,32}");
    }

    public enum HelpSection {
        OVERVIEW("overview"),
        PLAYBACK("playback"),
        QUEUE("queue"),
        LIBRARY("library"),
        ADMIN("admin");

        private final String id;

        HelpSection(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<HelpSection> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (HelpSection section : values()) {
                if (section.id.equals(normalized)) {
                    return Optional.of(section);
                }
            }
            return Optional.empty();
        }
    }

    public enum Decision {
        CONFIRM,
        CANCEL
    }

    public record ConfirmationAction(String token, Decision decision) {
    }
}
