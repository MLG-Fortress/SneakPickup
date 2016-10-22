import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by RoboMWM on 10/21/2016.
 */
public class SneakPickup extends JavaPlugin implements Listener
{
    FileConfiguration config = getConfig();
    YamlConfiguration storage;
    Set<World> disabledWorlds = new HashSet<>();
    Set<Player> remindedThisSession = new HashSet<>();
    Set<String> autoPickupBlocks = new HashSet<>();
    String reminderMessage = ChatColor.GOLD + "Hold sneak to pickup items";
    String noPermissionMessage = ChatColor.RED + "You do not have the sneakpickup.toggle permission";
    String enableMessage = "SneakPickup has been enabled";
    String disableMessage = "SneakPickup has been disabled";
    public void onEnable()
    {
        getServer().getPluginManager().registerEvents(this, this);
        config.addDefault("reminderMessage", "&6Hold sneak to pickup items");
        config.addDefault("noPermissionMessage", "&cYou do not have the sneakpickup.toggle permission");
        config.addDefault("enableMessage", "SneakPickup has been &aenabled");
        config.addDefault("disableMessage", "SneakPickup has been &cdisabled");
        config.addDefault("disabledWorlds", new ArrayList<>(Arrays.asList("prison", "creative")));
        config.addDefault("blocksToAlwaysPickup", new ArrayList<>(Arrays.asList("DIAMOND", "IRON_INGOT", "GOLD_INGOT", "COAL")));
        config.options().copyDefaults(true);
        saveConfig();
        for (String worldString : config.getStringList("disabledWorlds"))
        {
            World world = getServer().getWorld(worldString);
            if (world != null)
                disabledWorlds.add(world);
        }
        autoPickupBlocks = new HashSet<>(config.getStringList("blocksToAlwaysPickup"));
        reminderMessage = ChatColor.translateAlternateColorCodes('&', config.getString("reminderMessage"));
        noPermissionMessage = ChatColor.translateAlternateColorCodes('&', config.getString("noPermissionMessage"));
        enableMessage = ChatColor.translateAlternateColorCodes('&', config.getString("enableMessage"));
        disableMessage = ChatColor.translateAlternateColorCodes('&', config.getString("disableMessage"));

        //I use .data extension cuz reasons that have to do with how I automatically manage .yml files on my server so yea...
        //Not like they're supposed to touch this file anyways.
        File storageFile = new File(getDataFolder(), "storage.data");
        if (!storageFile.exists())
        {
            try
            {
                storageFile.createNewFile();
                storage = YamlConfiguration.loadConfiguration(storageFile);
            }
            catch (IOException e)
            {
                this.getLogger().severe("Could not create storage.yml! Since I'm lazy, there currently is no \"in memory\" option. Will now disable along with a nice stack trace for you to bother me with:");
                e.printStackTrace();
                getServer().getPluginManager().disablePlugin(this);
            }
        }
        else
            storage = YamlConfiguration.loadConfiguration(storageFile);
    }

    public void onDisable()
    {
        File storageFile = new File(getDataFolder(), "storage.data");
        if (config != null)
        {
            try
            {
                config.save(storageFile);
            }
            catch (IOException e) //really
            {
                e.printStackTrace();
            }
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if (!(sender instanceof Player) || (args.length > 1 && sender.hasPermission("sneakpickup.toggleothers")))
        {
            if (args.length < 1)
            {
                sender.sendMessage(ChatColor.RED + "/sneakpickup <player> - Toggles the player's sneak-to-pickup feature thingy");
                return true;
            }
            String message = toggleSneakPickup(getServer().getPlayerExact(args[0]));
            if (message == null)
                message = ChatColor.RED + args[0] + " is not online or is an invalid name.";
            else
                message = message + " for " + args[0];
            sender.sendMessage(message);
            return true;
        }

        Player player = (Player)sender;
        if (player.hasPermission("sneakpickup.toggle"))
            player.sendMessage(toggleSneakPickup(player));
        else
            player.sendMessage(noPermissionMessage);
        return true;
    }

    /**
     * Toggle's a player's sneak pickup feature. Does null checking.
     * @param player
     * @return Outputs a message whether the toggle was disabled or enabled, or null if player is null.
     */
    public String toggleSneakPickup(Player player)
    {
        if (player == null)
            return null;

        //SneakPickup is disabled if there is an entry in storage.yml
        if (storage.get(player.getUniqueId().toString()) != null)
        {
            //Enable
            storage.set(player.getUniqueId().toString(), null);
            return enableMessage;
        }
        else
        {
            //Disable
            storage.set(player.getUniqueId().toString(), true);
            return disableMessage;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    void onPlayerAttemptToPickupItem(PlayerPickupItemEvent event)
    {
        Player player = event.getPlayer();

        //Ignore disabled worlds
        //Ignore blocks we should always "auto pickup"
        if (disabledWorlds.contains(player.getWorld()) || autoPickupBlocks.contains(event.getItem().getItemStack().getType().toString()))
            return;

        //Ignore players who have disabled SneakPickup
        if (config.get(player.getUniqueId().toString()) != null)
            return;

        if (!remindedThisSession.contains(event.getPlayer()))
        {
            if (!player.hasPlayedBefore())
                player.sendMessage(reminderMessage);
            else
            {
                event.getItem().setCustomName(reminderMessage);
                event.getItem().setCustomNameVisible(true);
            }
            remindedThisSession.add(player);
        }
        if (!player.isSneaking())
            event.setCancelled(true);
    }

    @EventHandler
    void onPlayerQuitRemoveFromRemindedSet(PlayerQuitEvent event)
    {
        remindedThisSession.remove(event.getPlayer());
    }
}