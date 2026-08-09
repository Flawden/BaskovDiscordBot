package ru.flawden.BascovDiscordBot.config.eventconfig;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ru.flawden.BascovDiscordBot.operations.OperationalMetrics;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/**
 * Stateless dispatcher префиксных команд.
 */
@Slf4j
public class BotEvents extends ListenerAdapter {

    private final String prefix;
    private final CommandRegistry registry;
    private final CommandCooldowns cooldowns;
    private final OperationalMetrics operationalMetrics;

    public BotEvents(
            String prefix,
            List<Event> events,
            CommandCooldowns cooldowns,
            OperationalMetrics operationalMetrics) {
        if (prefix == null || prefix.isBlank() || prefix.length() > 5) {
            throw new IllegalArgumentException("Command prefix must contain 1-5 visible characters");
        }
        this.prefix = prefix;
        this.registry = new CommandRegistry(events);
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.operationalMetrics = Objects.requireNonNull(operationalMetrics, "operationalMetrics");
        log.info("BotEvents initialized with prefix '{}' and {} commands", prefix, registry.commands().size());
    }

    public List<Event> getCommands() {
        return registry.commands();
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        CommandInvocation invocation = CommandInvocation
                .parse(event.getMessage().getContentRaw(), prefix)
                .orElse(null);
        if (invocation == null) {
            return;
        }

        if (!event.isFromGuild()) {
            log.debug("Ignoring command '{}' outside a guild", invocation.commandName());
            return;
        }
        if (!(event.getChannel() instanceof TextChannel)) {
            log.debug("Ignoring command '{}' from unsupported channel type {} in guild {}",
                    invocation.commandName(), event.getChannelType(), event.getGuild().getId());
            return;
        }

        Member member = event.getMember();
        if (member == null) {
            log.warn("Ignoring command '{}' without guild member context in guild {}",
                    invocation.commandName(), event.getGuild().getId());
            return;
        }

        Event command = registry.find(invocation.commandName()).orElse(null);
        if (command == null) {
            log.info("Unknown command '{}' in guild {}", invocation.commandName(), event.getGuild().getId());
            send(event.getChannel().asTextChannel(), errorEmbed(
                    "❌ Неизвестная команда",
                    "Я не знаю эту команду. Используй `" + prefix + "help`, чтобы открыть список команд."));
            return;
        }

        if (command.needOwner() && !member.isOwner()) {
            log.warn("User {} cannot execute owner command '{}' in guild {}",
                    member.getId(), command.getName(), event.getGuild().getId());
            send(event.getChannel().asTextChannel(), errorEmbed(
                    "⚠️ Доступ ограничен",
                    "Эта команда доступна только создателю сервера."));
            return;
        }

        CommandCooldowns.Acquisition acquisition = cooldowns.tryAcquire(
                event.getGuild().getIdLong(),
                member.getIdLong(),
                command.getName(),
                command.cooldown());
        if (!acquisition.allowed()) {
            long seconds = Math.max(1L, (acquisition.remaining().toMillis() + 999L) / 1000L);
            send(event.getChannel().asTextChannel(), errorEmbed(
                    "⏳ Команда отдыхает",
                    "Попробуй снова через `" + seconds + "` сек."));
            return;
        }

        log.info("Executing command '{}' in guild {} by user {} with {} arguments",
                command.getName(), event.getGuild().getId(), member.getId(), invocation.arguments().size());
        try {
            command.execute(new EventArgs(event, invocation));
            operationalMetrics.recordSuccess(OperationalMetrics.Channel.PREFIX);
        } catch (RuntimeException exception) {
            operationalMetrics.recordFailure(OperationalMetrics.Channel.PREFIX, command.getName(), exception);
            log.error("Command '{}' failed in guild {} for user {}",
                    command.getName(), event.getGuild().getId(), member.getId(), exception);
            send(event.getChannel().asTextChannel(), errorEmbed(
                    "💥 Команда упала",
                    "Произошла внутренняя ошибка. Попробуй ещё раз чуть позже."));
        }
    }

    private MessageEmbed errorEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(Color.RED)
                .build();
    }

    private void send(TextChannel channel, MessageEmbed embed) {
        channel.sendMessageEmbeds(embed).queue(
                ignored -> { },
                failure -> log.warn("Failed to send command response to channel {}: {}",
                        channel.getId(), failure.getMessage()));
    }
}
