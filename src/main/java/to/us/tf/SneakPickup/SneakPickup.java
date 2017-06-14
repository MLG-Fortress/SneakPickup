package to.us.tf.SneakPickup;

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
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

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
    String inventoryFull = ChatColor.RED + "Your inventory is full!";
    public void onEnable()
    {
        getServer().getPluginManager().registerEvents(this, this);
        config.addDefault("reminderMessage", "&6Hold sneak to pickup");
        config.addDefault("noPermissionMessage", "&cYou do not have the sneakpickup.toggle permission");
        config.addDefault("enableMessage", "SneakPickup has been &aenabled");
        config.addDefault("disableMessage", "SneakPickup has been &cdisabled");
        config.addDefault("inventoryFull", "&cYour inventory is full!");
        config.addDefault("disabledWorlds", new ArrayList<>(Arrays.asList("prison", "creative")));
        config.addDefault("blocksToAlwaysPickup", new ArrayList<>(Arrays.asList("DIAMOND_ORE", "IRON_ORE", "GOLD_ORE", "COAL", "DIAMOND")));
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
        inventoryFull = ChatColor.translateAlternateColorCodes('&', config.getString("inventoryFull"));

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
        if (storage != null)
        {
            try
            {
                storage.save(storageFile);
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    void onPlayerAttemptToPickupItem(PlayerPickupItemEvent event)
    {
        Player player = event.getPlayer();

        //Ignore disabled worlds
        //Ignore blocks we should always "auto pickup"
        if (disabledWorlds.contains(player.getWorld()) || autoPickupBlocks.contains(event.getItem().getItemStack().getType().toString()))
            return;

        //Ignore players who have disabled SneakPickup
        if (storage.get(player.getUniqueId().toString()) != null)
            return;

        //Ignore arrow pickups
        if (event instanceof PlayerPickupArrowEvent)
            return;

        if (!remindedThisSession.contains(event.getPlayer()))
        {
            //If this item has a custom name already, don't change it.
            if (event.getItem().getCustomName() != null && !event.getItem().getCustomName().isEmpty())
                return;
            //Also send a message in chat to players who haven't played before
            if (!player.hasPlayedBefore())
                player.sendMessage(reminderMessage);
            event.getItem().setCustomName(reminderMessage);
            event.getItem().setCustomNameVisible(true);
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

    @EventHandler
    void onPlayerInventoryFullYetStillTryingToPickup(PlayerAttemptPickupItemEvent event)
    {
        Player player = event.getPlayer();

        if (player.isSneaking() && !hasSpace(player))
            player.sendActionBar(inventoryFull);
    }

    /**
     * Checks if player has a slot open in inventory
     * @param player
     * @return true if a slot is available
     */
    boolean hasSpace(Player player)
    {
        return player.getInventory().firstEmpty() != -1;
    }
}
