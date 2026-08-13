package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.flawden.BascovDiscordBot.recommendation.PersonalTasteProfile;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationFeedbackService;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/taste")
@ConditionalOnProperty(name = "baskov.product-api.enabled", havingValue = "true")
public class ProductTasteSignalApiController {

    private final ProductApiAccessGuard access;
    private final RecommendationFeedbackService feedback;

    public ProductTasteSignalApiController(
            ProductApiAccessGuard access,
            RecommendationFeedbackService feedback) {
        this.access = Objects.requireNonNull(access, "access");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
    }

    @PostMapping("/events")
    public ProductApiResponse.TasteSignalReceipt events(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam long guildId,
            @RequestBody ProductTasteSignalApiRequest.Batch request) {
        var principal = access.requireGuild(authorization, guildId);
        if (request == null) {
            throw new IllegalArgumentException("taste signal request cannot be null");
        }

        List<ValidatedSignal> signals = new ArrayList<>(request.events().size());
        for (ProductTasteSignalApiRequest.Event event : request.events()) {
            signals.add(validate(event));
        }

        for (ValidatedSignal signal : signals) {
            feedback.recordExternalSignal(
                    guildId,
                    principal.discordUserId(),
                    signal.artist(),
                    signal.title(),
                    signal.stableKey(),
                    signal.source(),
                    signal.outcome(),
                    signal.completionRatio());
        }

        PersonalTasteProfile profile = feedback.tasteProfile(guildId, principal.discordUserId());
        return new ProductApiResponse.TasteSignalReceipt(
                Long.toUnsignedString(guildId),
                principal.userId(),
                signals.size(),
                profile.evidenceSignals(),
                profile.confidence(),
                profile.positiveSignals(),
                profile.negativeSignals());
    }

    private static ValidatedSignal validate(ProductTasteSignalApiRequest.Event event) {
        if (event == null) {
            throw new IllegalArgumentException("taste event cannot be null");
        }
        if (event.type() == null || event.source() == null) {
            throw new IllegalArgumentException("taste event type and source are required");
        }
        if (event.title() == null || event.title().isBlank()) {
            throw new IllegalArgumentException("taste event title cannot be blank");
        }
        String stableKey = event.stableKey() == null ? "" : event.stableKey().trim();
        if (event.source() == ProductTasteSignalApiRequest.Source.LOCAL && stableKey.isBlank()) {
            throw new IllegalArgumentException("local taste event stableKey is required");
        }
        if (stableKey.length() > 320) {
            throw new IllegalArgumentException("taste event stableKey is too long");
        }
        String artist = bounded(event.artist(), 120);
        String title = bounded(event.title(), 180);
        double ratio = event.completionRatio() == null ? defaultRatio(event.type()) : event.completionRatio();
        if (!Double.isFinite(ratio) || ratio < 0.0d || ratio > 1.0d) {
            throw new IllegalArgumentException("completionRatio must be between 0 and 1");
        }
        String source = "android-" + event.source().name().toLowerCase(Locale.ROOT);
        return new ValidatedSignal(
                artist,
                title,
                stableKey,
                source,
                outcome(event.type()),
                ratio);
    }

    private static RecommendationOutcome outcome(ProductTasteSignalApiRequest.Type type) {
        return switch (type) {
            case PLAY -> RecommendationOutcome.PLAYED;
            case COMPLETED -> RecommendationOutcome.COMPLETED;
            case REPLAY -> RecommendationOutcome.REPLAYED;
            case QUICK_SKIP -> RecommendationOutcome.QUICK_SKIPPED;
            case STOP_EARLY -> RecommendationOutcome.QUICK_STOPPED;
            case FAVORITE_ADD -> RecommendationOutcome.FAVORITED;
            case FAVORITE_REMOVE -> RecommendationOutcome.UNFAVORITED;
        };
    }

    private static double defaultRatio(ProductTasteSignalApiRequest.Type type) {
        return switch (type) {
            case COMPLETED, REPLAY, FAVORITE_ADD, FAVORITE_REMOVE -> 1.0d;
            default -> 0.0d;
        };
    }

    private static String bounded(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "Неизвестно";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength).trim();
    }

    private record ValidatedSignal(
            String artist,
            String title,
            String stableKey,
            String source,
            RecommendationOutcome outcome,
            double completionRatio) {
    }
}
