package me.neatmonster.nocheatplus.checks.chat;

import java.lang.management.ManagementFactory;
import java.util.LinkedList;
import java.util.List;

import me.neatmonster.nocheatplus.EventManager;
import me.neatmonster.nocheatplus.NoCheatPlus;
import me.neatmonster.nocheatplus.NoCheatPlusPlayer;
import me.neatmonster.nocheatplus.config.ConfigurationCacheStore;
import me.neatmonster.nocheatplus.config.Permissions;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;

/**
 * Central location to listen to events that are
 * relevant for the chat checks
 * 
 */
public class ChatCheckListener implements Listener, EventManager {

    private final NoPwnageCheck      noPwnageCheck;
    private final ArrivalsLimitCheck arrivalsLimitCheck;
    private final ColorCheck         colorCheck;

    private final NoCheatPlus        plugin;

    public ChatCheckListener(final NoCheatPlus plugin) {

        this.plugin = plugin;

        noPwnageCheck = new NoPwnageCheck(plugin);
        arrivalsLimitCheck = new ArrivalsLimitCheck(plugin);
        colorCheck = new ColorCheck(plugin);

    }

    /**
     * We listen to PlayerChat events for obvious reasons
     * 
     * @param event
     *            The PlayerChat event
     */
    @EventHandler(
            ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void chat(final PlayerChatEvent event) {

        boolean cancelled = false;

        final NoCheatPlusPlayer player = plugin.getPlayer(event.getPlayer());
        final ChatConfig cc = ChatCheck.getConfig(player);
        final ChatData data = ChatCheck.getData(player);

        // Remember the original message
        data.message = event.getMessage();

        // Remember if it is a command or not
        if (event instanceof PlayerCommandPreprocessEvent)
            data.isCommand = true;
        else
            data.isCommand = false;

        // Now do the actual checks

        // First the nopwnage check
        if (cc.noPwnageCheck && !player.hasPermission(Permissions.CHAT_NOPWNAGE))
            cancelled = noPwnageCheck.check(player, data, cc);

        // Second the color check
        if (!cancelled && cc.colorCheck && !player.hasPermission(Permissions.CHAT_COLOR))
            cancelled = colorCheck.check(player, data, cc);

        // If one of the checks requested the event to be cancelled, do it
        if (cancelled)
            event.setCancelled(cancelled);
        else
            // In case one of the events modified the message, make sure that
            // the new message gets used
            event.setMessage(data.message);
    }

    /**
     * We listen to PlayerCommandPreprocess events because commands can be
     * used for spamming too.
     * 
     * @param event
     *            The PlayerCommandPreprocess Event
     */
    @EventHandler(
            priority = EventPriority.LOWEST)
    public void commandPreprocess(final PlayerCommandPreprocessEvent event) {

        final NoCheatPlusPlayer player = plugin.getPlayer(event.getPlayer());
        final ChatConfig cc = ChatCheck.getConfig(player);

        final String command = event.getMessage().split(" ")[0].substring(1).toLowerCase();

        // Protect the /plugins, /pl, /? commands to prevent players for seeing which plugins are installed
        if (cc.protectPlugins && (command.equals("plugins") || command.equals("pl") || command.equals("?"))
                && !event.getPlayer().hasPermission(Permissions.ADMIN_PLUGINS)) {
            event.getPlayer().sendMessage(
                    ChatColor.RED + "I'm sorry, but you do not have permission to perform this command. "
                            + "Please contact the server administrators if you believe that this is in error.");
            event.setCancelled(true);
            return;
        }

        // If OP by console only is enabled, prevent the op/deop commands
        // to be used by a player who is OP or has the required permissions
        if (cc.opByConsoleOnly
                && (command.equals("op")
                        && (event.getPlayer().isOp() || event.getPlayer().hasPermission("bukkit.command.op.give")) || command
                        .equals("deop")
                        && (event.getPlayer().isOp() || event.getPlayer().hasPermission("bukkit.command.op.take")))) {
            event.getPlayer().sendMessage(ChatColor.RED + "This command can only be executed from the console!");
            event.setCancelled(true);
            return;
        }

        // This type of event is derived from PlayerChatEvent, therefore
        // just treat it like that
        chat(event);
    }

    @Override
    public List<String> getActiveChecks(final ConfigurationCacheStore cc) {
        final LinkedList<String> s = new LinkedList<String>();

        final ChatConfig c = ChatCheck.getConfig(cc);
        if (c.noPwnageCheck)
            s.add("chat.nopwnage");
        if (c.arrivalsLimitCheck)
            s.add("chat.arrivalscheck");
        if (c.colorCheck)
            s.add("chat.color");
        return s;
    }

    /**
     * We listen to PlayerJoin events for the nopwnage check
     * 
     * @param event
     *            The PlayerJoin Event
     */
    @EventHandler(
            priority = EventPriority.LOWEST)
    public void join(final PlayerJoinEvent event) {

        final NoCheatPlusPlayer player = plugin.getPlayer(event.getPlayer());
        final ChatConfig cc = ChatCheck.getConfig(player);
        final ChatData data = ChatCheck.getData(player);

        // Check if the join is valid
        if (cc.noPwnageCheck && !player.hasPermission(Permissions.CHAT_NOPWNAGE))
            noPwnageCheck.handleJoin(player, data, cc);
    }

    /**
     * We listen to PlayerLogin events for the arrivalslimit check
     * 
     * @param event
     *            The PlayerLogin Event
     */
    @EventHandler(
            ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void login(final PlayerLoginEvent event) {

        // Do not check the players if the server has just restarted
        if (System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime() < 120000L)
            return;

        final NoCheatPlusPlayer player = plugin.getPlayer(event.getPlayer());
        final ChatConfig cc = ChatCheck.getConfig(player);
        final ChatData data = ChatCheck.getData(player);

        // Only check new players, not the regular players
        if (System.currentTimeMillis() - event.getPlayer().getFirstPlayed() > cc.arrivalsLimitNewTime)
            return;

        if (cc.arrivalsLimitCheck && arrivalsLimitCheck.check(player, data, cc))
            // If the player failed the check, disallow the login
            event.disallow(Result.KICK_OTHER, cc.arrivalsLimitKickMessage);
    }
}
