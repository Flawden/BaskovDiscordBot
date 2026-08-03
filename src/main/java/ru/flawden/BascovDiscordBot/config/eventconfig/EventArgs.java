package ru.flawden.BascovDiscordBot.config.eventconfig;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Objects;

/**
 * Неизменяемый контекст выполнения одной команды.
 */
public final class EventArgs {
    private final TextChannel textChannel;
    private final Member selfMember;
    private final Member member;
    private final Guild guild;
    private final JDA jda;
    private final Message message;
    private final String[] args;
    private final List<String> arguments;
    private final String rawArguments;
    private final GuildVoiceState selfVoiceState;
    private final GuildVoiceState memberVoiceState;

    EventArgs(MessageReceivedEvent event, CommandInvocation invocation) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(invocation, "invocation");

        this.textChannel = event.getChannel().asTextChannel();
        this.member = Objects.requireNonNull(event.getMember(), "Guild member");
        this.guild = event.getGuild();
        this.jda = event.getJDA();
        this.message = event.getMessage();
        this.selfMember = this.guild.getSelfMember();
        this.args = invocation.toLegacyArgs();
        this.arguments = invocation.arguments();
        this.rawArguments = invocation.rawArguments();
        this.selfVoiceState = this.selfMember.getVoiceState();
        this.memberVoiceState = this.member.getVoiceState();
    }

    public TextChannel getTextChannel() {
        return textChannel;
    }

    public Member getSelfMember() {
        return selfMember;
    }

    public Member getMember() {
        return member;
    }

    public Guild getGuild() {
        return guild;
    }

    public JDA getJda() {
        return jda;
    }

    public Message getMessage() {
        return message;
    }

    /**
     * Совместимый массив: индекс 0 содержит введённую команду, далее аргументы.
     */
    public String[] getArgs() {
        return args.clone();
    }

    public List<String> getArguments() {
        return arguments;
    }

    public String getRawArguments() {
        return rawArguments;
    }

    public GuildVoiceState getSelfVoiceState() {
        return selfVoiceState;
    }

    public GuildVoiceState getMemberVoiceState() {
        return memberVoiceState;
    }
}
