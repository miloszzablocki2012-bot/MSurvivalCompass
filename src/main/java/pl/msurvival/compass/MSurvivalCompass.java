package pl.msurvival.compass;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Locale;

public final class MSurvivalCompass extends JavaPlugin implements Listener {
    private NamespacedKey compassKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        compassKey = new NamespacedKey(this, "msurvival_compass");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("compassreload")) {
            if (!sender.hasPermission("msurvival.compass.admin")) return true;
            reloadConfig();
            sender.sendMessage(msg("reload"));
            return true;
        }

        if (!(sender instanceof Player p)) return true;

        if (cmd.equals("lobby")) {
            toLobby(p);
            return true;
        }

        if (cmd.equals("survival")) {
            toSurvival(p);
            return true;
        }

        if (cmd.equals("menu")) {
            openMenu(p);
            return true;
        }

        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player p = e.getPlayer();
            if (!p.isOnline()) return;
            if (isLobby(p)) giveCompassSafely(p);
            else removeCompass(p);
        }, 10L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (isLobby(p)) giveCompassSafely(p);
        else removeCompass(p);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (isCompass(e.getItem())) {
            e.setCancelled(true);
            openMenu(e.getPlayer());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isCompass(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (e.getView().getTitle().equals(color(getConfig().getString("menu.title")))) {
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item == null || !item.hasItemMeta()) return;

            String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
            p.closeInventory();

            if (name.contains("survival")) toSurvival(p);
            else if (name.contains("klucze")) p.performCommand("keysmenu");
            else if (name.contains("kity")) p.performCommand("kits");
            else if (name.contains("rynek")) p.performCommand("rynek");
            return;
        }

        if (isCompass(e.getCurrentItem()) || isCompass(e.getCursor())) {
            e.setCancelled(true);
        }
    }

    private void toLobby(Player p) {
        World w = Bukkit.getWorld(getConfig().getString("worlds.lobby", "Lobby"));
        if (w == null) {
            p.sendMessage(msg("no-world"));
            return;
        }
        p.teleport(w.getSpawnLocation());
        giveCompassSafely(p);
        p.sendMessage(msg("lobby"));
    }

    private void toSurvival(Player p) {
        removeCompass(p);
        World w = Bukkit.getWorld(getConfig().getString("worlds.survival", "world"));
        if (w == null) {
            p.sendMessage(msg("no-world"));
            return;
        }
        p.teleport(w.getSpawnLocation());
        p.sendMessage(msg("survival"));
    }

    private void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color(getConfig().getString("menu.title", "&6&lMSURVIVAL MENU")));
        ItemStack glass = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);

        inv.setItem(11, named(Material.GRASS_BLOCK, getConfig().getString("menu.survival-name")));
        inv.setItem(13, named(Material.TRIPWIRE_HOOK, getConfig().getString("menu.keys-name")));
        inv.setItem(14, named(Material.CHEST, getConfig().getString("menu.kits-name")));
        inv.setItem(15, named(Material.GOLD_INGOT, getConfig().getString("menu.market-name")));

        p.openInventory(inv);
    }

    private void giveCompassSafely(Player p) {
        removeCompass(p);

        ItemStack compass = compass();
        int slot = getConfig().getInt("compass.slot", 4);

        if (slot >= 0 && slot < p.getInventory().getSize()) {
            ItemStack current = p.getInventory().getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                p.getInventory().setItem(slot, compass);
                return;
            }
        }

        int empty = p.getInventory().firstEmpty();
        if (empty != -1) {
            p.getInventory().setItem(empty, compass);
        }
    }

    private void removeCompass(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isCompass(contents[i])) p.getInventory().setItem(i, null);
        }
    }

    private ItemStack compass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString("compass.name", "&6&lMSurvival Menu")));

        ArrayList<String> lore = new ArrayList<>();
        for (String line : getConfig().getStringList("compass.lore")) lore.add(color(line));
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.STRING, "true");
        item.setItemMeta(meta);
        return item;
    }

    private boolean isCompass(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.STRING);
    }

    private boolean isLobby(Player p) {
        return p.getWorld().getName().equalsIgnoreCase(getConfig().getString("worlds.lobby", "Lobby"));
    }

    private ItemStack named(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        item.setItemMeta(meta);
        return item;
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
