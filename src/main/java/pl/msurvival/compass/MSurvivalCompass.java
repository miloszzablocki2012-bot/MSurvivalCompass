package pl.msurvival.compass;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public final class MSurvivalCompass extends JavaPlugin implements Listener, TabExecutor {
    private NamespacedKey actionKey;
    private NamespacedKey compassKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        actionKey = new NamespacedKey(this, "menu_action");
        compassKey = new NamespacedKey(this, "lobby_compass");
        Bukkit.getPluginManager().registerEvents(this, this);
        reg("mscompass"); reg("lobby"); reg("survival"); reg("setmsspawn");
        getLogger().info("MSurvivalCompass 9.0-github-design wlaczony.");
    }

    private void reg(String cmd) { if (getCommand(cmd) != null) getCommand(cmd).setExecutor(this); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("mscompass")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("msurvival.compass.admin")) { sender.sendMessage(color(msg("no-permission", "&cBrak uprawnien."))); return true; }
                reloadConfig();
                sender.sendMessage(color(msg("reload", "&aPrzeladowano.")));
                return true;
            }
            sender.sendMessage(color("&6◆ &e&lMSURVIVAL &7» &f/mscompass reload"));
            return true;
        }
        if (!(sender instanceof Player player)) return true;
        if (cmd.equals("lobby")) { teleportToWorld(player, getLobbyWorld(), "lobby"); return true; }
        if (cmd.equals("survival")) { teleportToWorld(player, getSurvivalWorld(), "survival"); return true; }
        if (cmd.equals("setmsspawn")) {
            if (!player.hasPermission("msurvival.compass.admin")) { player.sendMessage(color(msg("no-permission", "&cBrak uprawnien."))); return true; }
            if (args.length < 1) { player.sendMessage(color("&cUzycie: /setmsspawn <lobby|survival>")); return true; }
            String type = args[0].equalsIgnoreCase("lobby") ? "lobby" : "survival";
            Location loc = player.getLocation();
            getConfig().set("spawns." + type + ".world", loc.getWorld().getName());
            getConfig().set("spawns." + type + ".x", loc.getX());
            getConfig().set("spawns." + type + ".y", loc.getY());
            getConfig().set("spawns." + type + ".z", loc.getZ());
            getConfig().set("spawns." + type + ".yaw", loc.getYaw());
            getConfig().set("spawns." + type + ".pitch", loc.getPitch());
            saveConfig();
            player.sendMessage(color(msg("spawn-set", "&aUstawiono spawn %type%")
                    .replace("%type%", type).replace("%world%", loc.getWorld().getName())));
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("setmsspawn") && args.length == 1) return filter(List.of("lobby", "survival"), args[0]);
        if (command.getName().equalsIgnoreCase("mscompass") && args.length == 1) return filter(List.of("reload"), args[0]);
        return Collections.emptyList();
    }

    private List<String> filter(Collection<String> list, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return list.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> giveCompassIfLobby(event.getPlayer()), 10L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> giveCompassIfLobby(event.getPlayer()), 5L);
    }

    private void giveCompassIfLobby(Player player) {
        if (!getConfig().getBoolean("compass.enabled", true)) return;
        if (!player.getWorld().getName().equalsIgnoreCase(getLobbyWorld())) return;
        int slot = getConfig().getInt("compass.slot", 4);
        if (slot < 0 || slot > 35) slot = 4;
        player.getInventory().setItem(slot, makeCompass());
    }

    private ItemStack makeCompass() {
        ItemStack item = new ItemStack(mat(getConfig().getString("compass.material", "COMPASS"), Material.COMPASS));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(getConfig().getString("compass.name", "&6◆ &e&lMSURVIVAL &7» &fMenu")));
            meta.setLore(colorList(getConfig().getStringList("compass.lore")));
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.STRING, "true");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isCompass(item)) return;
        event.setCancelled(true);
        openMenu(event.getPlayer());
    }

    private boolean isCompass(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(compassKey, PersistentDataType.STRING);
    }

    private void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new MenuHolder(), 27, color(getConfig().getString("menu.title", "&6◆ &e&lMSURVIVAL &7» &fMENU")));
        fill(inv);
        inv.setItem(10, menuItem(Material.GRASS_BLOCK, getConfig().getString("menu.survival-name", "&e&lSURVIVAL &7» &fWejdź"), "survival",
                List.of("&7» &fKliknij, aby przejść na &eSurvival&f.")));
        inv.setItem(11, menuItem(Material.TRIPWIRE_HOOK, getConfig().getString("menu.keys-name", "&a&lKLUCZE &7» &fNagrody"), "keys",
                List.of("&7» &fOtwiera menu &a/keysmenu&f.")));
        inv.setItem(13, menuItem(Material.CHEST, getConfig().getString("menu.kits-name", "&d&lE-KITY &7» &fZestawy"), "kits",
                List.of("&7» &fOtwiera menu &d/kits&f.")));
        inv.setItem(15, menuItem(Material.EMERALD, getConfig().getString("menu.market-name", "&2&lRYNEK &7» &fHandel"), "market",
                List.of("&7» &fOtwiera &2/rynek&f.")));
        inv.setItem(16, menuItem(Material.BEACON, "&b&lLOBBY &7» &fPowrót", "lobby",
                List.of("&7» &fPowrót do &blobby&f.")));
        player.openInventory(inv);
    }

    private void fill(Inventory inv) {
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, " ", "none", Collections.emptyList());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private ItemStack menuItem(Material material, String name, String action, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(colorList(lore));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.equals("none")) return;
        player.closeInventory();
        switch (action) {
            case "survival" -> teleportToWorld(player, getSurvivalWorld(), "survival");
            case "lobby" -> teleportToWorld(player, getLobbyWorld(), "lobby");
            case "keys" -> player.performCommand("keysmenu");
            case "kits" -> player.performCommand("kits");
            case "market" -> player.performCommand("rynek");
        }
    }

    private void teleportToWorld(Player player, String worldName, String type) {
        Location loc = getSpawn(type);
        if (loc == null) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) { player.sendMessage(color(msg("no-world", "&cBrak świata %world%").replace("%world%", worldName))); return; }
            loc = world.getSpawnLocation().add(0.5, 0, 0.5);
        }
        player.teleport(loc);
        player.sendMessage(color(msg(type.equals("lobby") ? "lobby" : "survival", "&aTeleport.")));
    }

    private Location getSpawn(String type) {
        FileConfiguration cfg = getConfig();
        String path = "spawns." + type;
        String w = cfg.getString(path + ".world", null);
        if (w == null) return null;
        World world = Bukkit.getWorld(w);
        if (world == null) return null;
        return new Location(world, cfg.getDouble(path + ".x"), cfg.getDouble(path + ".y"), cfg.getDouble(path + ".z"),
                (float) cfg.getDouble(path + ".yaw"), (float) cfg.getDouble(path + ".pitch"));
    }

    private String getLobbyWorld() { return getConfig().getString("worlds.lobby", "lobby"); }
    private String getSurvivalWorld() { return getConfig().getString("worlds.survival", "world"); }
    private String msg(String k, String def) { return getConfig().getString("messages." + k, def); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private List<String> colorList(List<String> list) { return list == null ? Collections.emptyList() : list.stream().map(this::color).collect(Collectors.toList()); }
    private Material mat(String name, Material def) { try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception e) { return def; } }

    private static final class MenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
