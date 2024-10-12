package ru.flawden.BascovDiscordBot.config.eventconfig;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * Класс EventArgs содержит аргументы события, передаваемые при выполнении команд бота.
 * Он инкапсулирует информацию о текстовом канале, участниках, сообщениях и других аспектах
 * события, чтобы упростить доступ к необходимым данным в обработчиках событий.
 *
 * @author Flawden
 * @version 1.0
 */
public class EventArgs {
    private final TextChannel textChannel;
    private final Member selfMember;
    private final Member member;
    private final Guild guild;
    private final JDA jda;
    private final Message message;
    private final String[] args;
    private final GuildVoiceState selfVoiceState;
    private final GuildVoiceState memberVoiceState;

    protected EventArgs(MessageReceivedEvent event) {
        this.textChannel = event.getChannel().asTextChannel();
        this.member = event.getMember();
        this.guild = event.getGuild();
        this.jda = event.getJDA();
        this.message = event.getMessage();
        this.selfMember = this.guild.getSelfMember();
        this.args = this.message.getContentRaw().split(" ");
        this.selfVoiceState = this.selfMember.getVoiceState();
        this.memberVoiceState = this.member.getVoiceState();
    }

    /**
     * Возвращает текстовый канал, в котором произошло событие.
     *
     * @return текстовый канал
     */
    public TextChannel getTextChannel() {
        return this.textChannel;
    }

    /**
     * Возвращает самого бота (участника).
     *
     * @return участник, являющийся ботом
     */
    public Member getSelfMember() {
        return this.selfMember;
    }

    /**
     * Возвращает участника, отправившего сообщение.
     *
     * @return член гильдии, отправивший сообщение
     */
    public Member getMember() {
        return this.member;
    }

    /**
     * Возвращает группу, в которой произошло событие.
     *
     * @return гильдия
     */
    public Guild getGuild() {
        return this.guild;
    }

    /**
     * Возвращает объект JDA, представляющий интерфейс Discord API.
     *
     * @return объект JDA
     */
    public JDA getJda() {
        return this.jda;
    }

    /**
     * Возвращает сообщение, вызвавшее событие.
     *
     * @return объект сообщения
     */
    public Message getMessage() {
        return this.message;
    }

    /**
     * Возвращает аргументы команды, разделенные пробелами.
     *
     * @return массив аргументов команды
     */
    public String[] getArgs() {
        return this.args;
    }

    /**
     * Возвращает состояние голосового канала бота.
     *
     * @return состояние голосового канала бота
     */
    public GuildVoiceState getSelfVoiceState() {
        return this.selfVoiceState;
    }

    /**
     * Возвращает состояние голосового канала участника, отправившего сообщение.
     *
     * @return состояние голосового канала участника
     */
    public GuildVoiceState getMemberVoiceState() {
        return this.memberVoiceState;
    }

}
