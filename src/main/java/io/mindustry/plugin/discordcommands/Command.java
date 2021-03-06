package io.mindustry.plugin.discordcommands;

/** Represents a command */
public abstract class Command {
    public String name;
    /** Help for this command, shown by the help command */
    public String help = "No help is availble for this command";

    public Command(String name) {
        // always ALWAYS lowercase command names
        this.name = name.toLowerCase();
    }

    /**
     * This method is called when the command is run
     * @param ctx
     */
    public abstract void run(Context ctx);

    public boolean hasPermission(Context ctx) {
        return true;
    }
}
