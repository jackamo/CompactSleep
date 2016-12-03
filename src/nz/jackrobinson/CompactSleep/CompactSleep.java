package nz.jackrobinson.CompactSleep;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.UUID;

public class CompactSleep extends JavaPlugin implements Listener{

    private int minPlayers;
    private boolean usePercentage;
    private double sleepPercentage;
    private long delayTime;

    private String sleepMessage;
    private String leaveMessage;
    private String wakeMessage;

    private HashSet<UUID> asleepPlayers = new HashSet<UUID>();
    private int delayedTask = 0;
    private boolean sleepCancelled = false;



    //
    // PLUGIN RELATED INFO
    //

    public void onEnable(){
        saveDefaultConfig();
        loadConfig();

        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadConfig(){
        reloadConfig();
        FileConfiguration config = getConfig();
        // Initialise the config fields.
        minPlayers = config.getInt("minPlayers");
        sleepPercentage = config.getDouble("sleepPercentage");
        usePercentage = config.getBoolean("usePercentage");
        delayTime = config.getLong("delayTime");

        // TODO: These could be better in the config?
        sleepMessage = ChatColor.translateAlternateColorCodes('&', "&b{player}&6 is now sleeping. &f[&l&cCANCEL&f]");
        wakeMessage = ChatColor.translateAlternateColorCodes('&', "&6Rise and shine... Good morning everyone!");
        leaveMessage = ChatColor.translateAlternateColorCodes('&', "&b{player}&6 is no longer sleeping.");

    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(command.getName().equalsIgnoreCase("csreload") || command.getName().equalsIgnoreCase("compactsleepreload")){
            this.loadConfig();
            String message = ChatColor.translateAlternateColorCodes('&', "&f[&cCompactSleep&f] &6Config reloaded!");
            sender.sendMessage(message);
            if(usePercentage){
                message = ChatColor.translateAlternateColorCodes('&', "&f[&cCompactSleep&f] &6Using '&7percentage&6' setting");
                sender.sendMessage(message);
                message = ChatColor.translateAlternateColorCodes('&', "&f[&cCompactSleep&f] &6Currently set to &9" + sleepPercentage * 100 + "%&6 of online players");
                sender.sendMessage(message);
            }else{
                message = ChatColor.translateAlternateColorCodes('&', "&f[&cCompactSleep&f] &6Using '&7minimum number of players&6' setting");
                sender.sendMessage(message);
                message = ChatColor.translateAlternateColorCodes('&', "&f[&cCompactSleep&f] &6Currently set to &9" + minPlayers + "&6 players");
                sender.sendMessage(message);
            }
            message = ChatColor.translateAlternateColorCodes('&', "&f[&cCompactSleep&f] &6Request timeframe is equal to &9" + delayTime + "&6 ticks");
            sender.sendMessage(message);
            return true;
        }

        if(command.getName().equalsIgnoreCase("cscancel")){
            if(asleepPlayers.size() < 1){
                return true;
            }
            World world = getServer().getPlayer(sender.getName()).getWorld();
            Bukkit.getScheduler().cancelTask(delayedTask);

            String message = ChatColor.translateAlternateColorCodes('&', "&b{player}&6 has requested a cancel.");
            broadcast(world, getServer().getPlayer(sender.getName()), message);
            delayedTask = 0;
            sleepCancelled = true;
            return true;
        }

        return false;
    }

    //
    // BED RELATED EVENTS
    //

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event){
        if(event.isCancelled() || delayedTask != 0){
            return;
        }
        World world = event.getBed().getWorld();
        asleepPlayers.add(event.getPlayer().getUniqueId());
        broadcastCommand(world, event.getPlayer(), sleepMessage, "/cscancel");
        if(!sleepCancelled){
            delayedTask = getServer().getScheduler().scheduleSyncDelayedTask(this, () -> check(world), delayTime);
        }

        sleepCancelled = false;
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event){
        if(isNight(event.getBed().getWorld())){
            broadcast(event.getPlayer().getWorld(), event.getPlayer(), leaveMessage);
        }
        asleepPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        asleepPlayers.remove(event.getPlayer().getUniqueId());
    }

    //
    // VALIDATION
    //

    private void check(World world){

        if(usePercentage){
            checkWithMinPercentage(world);
        }else{
            checkWithMinNum(world);
        }
        sleepCancelled = false;
        delayedTask = 0;
    }

    private void checkWithMinPercentage(World world){
        if(asleepPlayers.size() / world.getPlayers().size() >= sleepPercentage || asleepPlayers.size() == world.getPlayers().size()){
            setWorldToNextDay(world);
        }
    }

    private void checkWithMinNum(World world){
        if(asleepPlayers.size() >= minPlayers || asleepPlayers.size() == world.getPlayers().size()){
            setWorldToNextDay(world);
        }
    }

    //
    // MESSAGING
    //

    private void broadcast(World world, Player player, String message){
        world.getPlayers().forEach(p -> {
            String msg = message.replace("{player}", player.getDisplayName());
            p.sendMessage(msg);
        });
    }

    private void broadcastCommand(World world, Player player, String message, String command) {
        TextComponent textComponent = new TextComponent(message.replace("{player}", player.getDisplayName()));
        textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));

        getServer().spigot().broadcast(textComponent);
    }

    //
    // HELPERS
    //

    private void setWorldToNextDay(World world){
        // Remove all storms
        if(world.hasStorm()){
            world.setStorm(false);
        }
        if(world.isThundering()){
            world.setThundering(false);
        }
        // Set time to morning
        world.setTime(24000);
        // Clear all asleep player records.
        broadcast(world, null, wakeMessage);
        asleepPlayers.clear();
    }

    private boolean isNight(World w)
    {
        long time = (w.getFullTime()) % 24000;
        return time >= 13000 && time < 23600;
    }







}
