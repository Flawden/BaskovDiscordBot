# Baskov Discord Bot
## Made by Flawden

A music discord bot with a comic name that takes advantage of Spring Boot in development.

## Features

- Implemented music playback, pause, track skip, tracklist creation.
- Easy to add new commands thanks to the competent interaction of the Spring Framework and JDA.

The bot is easy to use and modify. It is not necessary to fully understand how it works in order to teach it new commands.

# Preparation for use

Before using, you need to get the project on your device. To do this, use the "Code" button at the top of the page with the repository and choose a convenient method for obtaining it, or use the terminal command

> git clone https://github.com/Flawden/BaskovDiscordBot.git

Next, you need to enter the token of your discord bot. To do this, you need to follow the path:

> src/main/resources

Make a copy of the application.properties.origin file, removing the ".origin" extension from the name of the resulting copy (You should get "application.properties")

Open the resulting file and add your token. It should look something like this:

> discordBot.token = (YOUR_TOKEN)

Interaction with the bot is carried out through commands in the chat. The bot considers as commands any messages that start with a special prefix that you can configure by following the path

> src/main/java/en/flawden/BascovDiscordBot/config/BotConfig.java

And replacing "!" with any prefix convenient for you in the string:

> BotEvents botEvents = new BotEvents("!");

# How the bot works

The bot's job is to exchange messages between the discord servers and your bot. The bot listens to the discord server or its private messages, and in the event of an event, it performs one or another action.

Messages starting with a special prefix (which is set in the way described above) are considered by the bot as a command. New commands are created as follows.

In the "commands" folder or its subfolders, a class with any name is created (it is preferable that the name ends with "Event"). This class must be inherited from the Event interface

> Full interface path for import: ru.flawden.BascovDiscordBot.config.eventconfig.Event;

Interface obliges to override 4 methods

1) execute - this method is executed as soon as the bot recognizes your command in the chat.
2) getName - the name of the command that must be entered to activate it in the chat. (Specify without prefix)
3) helpMessage - a hint that will appear for the command when it is displayed in the list of commands.
4) needOwner - is the command available only to the creator of the server

By creating a class and inheriting it from the interface, you have created a command and it will automatically be added to the command stack for the bot.

# Application assembly

You can build the application with Maven

1) Through the IDE (I'll show you with Intellij Idea as an example)
2) Using the command line

## Building with the IDE

To assemble, follow these steps:

1) Run the project in IntelliJ IDEA IDE
2) Open the Maven menu
3) Go to Lifecycle tab
4) Click clean
5) Click package

You now have a WAR ready to upload to your server.
(If for some reason you can't find the Maven menu, the online guides will surely help you)

## Building with Maven

To build with [Maven](https://maven.apache.org/), first make sure you have it installed on your computer.

If maven is already installed - open a command line and navigate to the project folder using the command
> cd <Path_to_Project>

Next, enter the following 2 commands in turn:
> mvn clean
> 
> mvn install

A "Target" folder will appear in the root of the project. Log into it via the console. The folder will contain a .jar file

Run it with the command:
>  java -jar <Full_name_of_jar_file>
