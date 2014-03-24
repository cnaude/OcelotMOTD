package com.cnaude.ocelotmotd;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import it.sauronsoftware.cron4j.Scheduler;
import java.util.ArrayList;
import java.util.HashMap;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 *
 * @author cnaude
 */
public class OcelotMOTD extends JavaPlugin implements Listener {

    static final Logger log = Logger.getLogger("Minecraft");
    private static String LOG_HEADER;
    private boolean debugEnabled;
    private Scheduler scheduler;
    private final HashMap<String, String> joinMessages = new HashMap<>();
    private static Permission perms = null;
    private final ArrayList<String> tasks = new ArrayList<>();
    private final String CRONTABS = "crontabs";
    private final String ENABLED = "enabled";
    private final String CHRONOS = "chronos";
    private final String MESSAGE = "message";
    private final String COMMAND = "command";

    @Override
    public void onEnable() {
        LOG_HEADER = "[" + this.getName() + "]";

        if (!setupPermissions()) {
            logError("Vault not detected!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        scheduler = new Scheduler();
        this.getConfig().options().copyDefaults(true);
        saveConfig();
        loadConfig();
        scheduler.start();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        scheduler.stop();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (sender.hasPermission("ocelotmotd.reload")) {
            logInfo("Stopping scheduler ...");
            scheduler.stop();
            for (String task : tasks) {
                logDebug("[Deschedule Task]: " + task);
                scheduler.deschedule(task);
            }
            sender.sendMessage("Reloading config.yml ...");
            logInfo("Reloading config.yml ...");
            reloadConfig();
            this.getConfig().options().copyDefaults(true);
            loadConfig();
            logInfo("Starting scheduler ...");
            scheduler.start();
        }
        return true;
    }

    private void loadConfig() {
        debugEnabled = getConfig().getBoolean("debug");
        logDebug("Debug enabled");
        for (String group : getConfig().getConfigurationSection("on-join-motd").getKeys(false)) {
            if (!joinMessages.containsKey(group)) {
                joinMessages.put(group, ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("on-join-motd." + group)));
            }
        }

        for (final String group : getConfig().getConfigurationSection(CRONTABS).getKeys(false)) {
            logDebug("[Group]: " + group);
            for (String name : getConfig().getConfigurationSection(CRONTABS + "." + group).getKeys(false)) {
                logDebug("  [Name]: " + name);
                if (getConfig().getBoolean(CRONTABS + "." + group + "." + name + "." + ENABLED)) {
                    logDebug("    [Enabled]: true");
                    String chronos = getConfig().getString(CRONTABS + "." + group + "." + name + "." + CHRONOS);
                    final String message = ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString(CRONTABS + "." + group + "." + name + "." + MESSAGE));
                    final String command = ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString(CRONTABS + "." + group + "." + name + "." + COMMAND));
                    logDebug("    [Chronos]: " + chronos);
                    logDebug("    [Message]: " + message);
                    logDebug("    [Command]: " + command);
                    String task = scheduler.schedule(chronos, new Runnable() {
                        @Override
                        public void run() {
                            if (!message.isEmpty()) {
                                sendMessageToGroup(group, message);
                            }
                            if (!command.isEmpty()) {
                                getServer().dispatchCommand(getServer().getConsoleSender(), command);
                            }
                        }
                    });
                    logDebug("[Add Task]: " + task);
                    tasks.add(task);
                } else {
                    logDebug("    [Enabled]: false");
                }
            }
        }
    }

    private void sendMessageToGroup(String group, String message) {
        logDebug("[Group: " + group + "] [Message: " + message);
        for (Player player : getServer().getOnlinePlayers()) {
            String playerGroup = perms.getPrimaryGroup(player);
            logDebug("[" + player.getName() + "] " + playerGroup);
            if (group == null) {
                continue;
            }
            if (playerGroup.equalsIgnoreCase(group)) {
                player.sendMessage(tokenizeMessage(player, message));
            }
        }
    }

    private String tokenizeMessage(Player player, String message) {
        return message
                .replace("%NAME%", player.getName())
                .replace("%DISPLAYNAME%", player.getDisplayName())
                .replace("%WORLD%", player.getWorld().getName());
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();
        return perms != null;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String primaryGroup = perms.getPrimaryGroup(player);
        logDebug("[" + player.getName() + "] " + primaryGroup);
        if (primaryGroup == null) {
            return;
        }
        for (String groupName : joinMessages.keySet()) {
            if (primaryGroup.equalsIgnoreCase(groupName)) {
                logDebug("[" + player.getName() + "] " + joinMessages.get(groupName));
                player.sendMessage(tokenizeMessage(player, joinMessages.get(groupName)));
                return;
            } 
        }
    }

    public void logInfo(String _message) {
        log.log(Level.INFO, String.format("%s %s", LOG_HEADER, _message));
    }

    public void logError(String _message) {
        log.log(Level.SEVERE, String.format("%s %s", LOG_HEADER, _message));
    }

    public void logDebug(String _message) {
        if (debugEnabled) {
            log.log(Level.INFO, String.format("%s [DEBUG] %s", LOG_HEADER, _message));
        }
    }

}
