package de.codingair.codingapi.server.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class CommandBuilder implements CommandExecutor, TabCompleter {
    private static final HashMap<String, CommandBuilder> REGISTERED = new HashMap<>();
    private static Listener listener;

    private static void registerListener(JavaPlugin plugin) {
        if(listener != null) return;

        Bukkit.getPluginManager().registerEvents(listener = new Listener() {

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onPreProcess(PlayerCommandPreprocessEvent e) {
                String label = e.getMessage().split(" ")[0].replaceFirst("/", "");
                Command command = Bukkit.getPluginCommand(label);

                if(command == null || command.getName() == null) return;

                CommandBuilder builder = REGISTERED.get(command.getName().toLowerCase());

                if(builder == null) return;
                if(!builder.isHighestPriority()) return;

                e.setCancelled(true);
                builder.onCommand(e.getPlayer(), command, label, e.getMessage().replaceFirst("/" + label + " ", "").split(" "));
            }

        }, plugin);
    }

    private String name;
    private BaseComponent baseComponent;
    private boolean tabCompleter;
    private boolean highestPriority = false;

    public CommandBuilder(String name, BaseComponent baseComponent, boolean tabCompleter) {
        this.name = name;
        this.baseComponent = baseComponent;
        this.tabCompleter = tabCompleter;
    }

    public void register(JavaPlugin plugin) {
        if(isRegistered()) return;

        if(plugin.getCommand(this.name) == null) throw new IllegalStateException("You must first add the command to the plugin.yml!");

        plugin.getCommand(this.name).setExecutor(this);
        if(tabCompleter) plugin.getCommand(this.name).setTabCompleter(this);

        REGISTERED.put(this.name.toLowerCase(), this);

        registerListener(plugin);
    }

    public void unregister(JavaPlugin plugin) {
        if(!isRegistered()) return;

        plugin.getCommand(this.name).setExecutor(null);
        plugin.getCommand(this.name).setTabCompleter(null);

        REGISTERED.remove(this.name.toLowerCase());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CommandComponent component = (args.length == 1 && args[0].equals("/" + label)) ? getBaseComponent() : getComponent(args);

        if(component == null) {
            this.baseComponent.unknownSubCommand(sender, label, args);
            return false;
        }

        if(component.isOnlyConsole() && sender instanceof Player) {
            this.baseComponent.onlyFor(false, sender, label, component);
            return false;
        }

        if(component.isOnlyPlayers() && !(sender instanceof Player)) {
            this.baseComponent.onlyFor(true, sender, label, component);
            return false;
        }

        if(component.hasPermission(sender)) {
            return component.runCommand(sender, label, args);
        }

        this.baseComponent.noPermission(sender, label, component);
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> sub = new ArrayList<>();
        List<String> sug = new ArrayList<>();

        if(args.length == 0) return sug;

        String lastArg = args[args.length - 1];
        args[args.length - 1] = "";
        CommandComponent component = getComponent(args);

        if(component == null) return sug;

        for(CommandComponent child : component.getChildren()) {
            if(child.hasPermission(sender)) {
                if(child instanceof MultiCommandComponent) ((MultiCommandComponent) child).addArguments(sender, sub);
                else sub.add(child.getArgument());
            }
        }

        for(String subCommand : sub) {
            if(subCommand.toLowerCase().startsWith(lastArg.toLowerCase())) {
                sug.add(subCommand);
            }
        }

        if(sug.isEmpty()) return sub;
        else sub.clear();
        return sug;
    }

    public CommandComponent getComponent(String... args) {
        if(args.length == 0) return this.baseComponent;
        return getComponent(Arrays.asList(args));
    }

    public CommandComponent getComponent(List<String> s) {
        if(s.isEmpty() || (s.size() == 1 && s.get(0).isEmpty())) return this.baseComponent;

        CommandComponent current = this.baseComponent;

        for(String value : s) {
            if(current == null || value.isEmpty() || current.getChild(value) == null) break;
            current = current.getChild(value);
        }

        return current;
    }

    public String getName() {
        return name;
    }

    public BaseComponent getBaseComponent() {
        return baseComponent;
    }

    public boolean isTabCompleter() {
        return tabCompleter;
    }

    public boolean isRegistered() {
        return REGISTERED.containsKey(this.name.toLowerCase());
    }

    public boolean isHighestPriority() {
        return highestPriority;
    }

    public CommandBuilder setHighestPriority(boolean highestPriority) {
        this.highestPriority = highestPriority;
        return this;
    }
}
