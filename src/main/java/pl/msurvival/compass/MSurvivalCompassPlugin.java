package pl.msurvival.compass;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MSurvivalCompassPlugin extends JavaPlugin implements Listener {

    private NamespacedKey compassKey;
    private NamespacedKey menuActionKey;

    private String lobbyWorld;
    private int compassSlot;
    private boolean forceSlot;
    private boolean removeOnNonLobby;
    private boolean cleanupOldByName;
    private List<String> oldNameContains;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("menu")).setExecutor(this);
        Objects.requireNonNull(getCommand("compassreload")).setExecutor(this);
        Objects.requireNonNull(getCommand("compassfix")).setExecutor(this);

        if (getConfig().getBoolean("settings.update-task-enabled", true)) {
            long period = Math.max(10L, getConfig().getLong("settings.update-task-ticks", 40L));
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    fixCompass(player);
                }
            }, period, period);
        }

        getLogger().info("MSurvivalCompass wlaczony. Lobby world: " + lobbyWorld + ", slot: " + compassSlot);
    }

    private void loadSettings() {
        this.compassKey = new NamespacedKey(this, "msurvival_compass");
        this.menuActionKey = new NamespacedKey(this, "msurvival_menu_action");
        this.lobbyWorld = getConfig().getString("settings.lobby-world", "lobby");
        this.compassSlot = Math.max(0, Math.min(35, getConfig().getInt("settings.compass-slot", 4)));
        this.forceSlot = getConfig().getBoolean("settings.force-slot", true);
        this.removeOnNonLobby = getConfig().getBoolean("cleanup.remove-on-non-lobby", true);
        this.cleanupOldByName = getConfig().getBoolean("cleanup.remove-old-menu-compasses-by-name", true);
        this.oldNameContains = new ArrayList<>();
        for (String s : getConfig().getStringList("cleanup.old-name-contains")) {
            if (s != null && !s.isBlank()) oldNameContains.add(ChatColor.stripColor(color(s)).toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (name.equals("compassreload")) {
            if (!sender.hasPermission("msurvivalcompass.admin")) {
                sender.sendMessage(color(msg("no-permission")));
                return true;
            }
            reloadConfig();
            loadSettings();
            sender.sendMessage(color(msg("reloaded")));
            for (Player player : Bukkit.getOnlinePlayers()) fixCompass(player);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(msg("only-player")));
            return true;
        }

        if (name.equals("compassfix")) {
            if (!player.hasPermission("msurvivalcompass.admin")) {
                player.sendMessage(color(msg("no-permission")));
                return true;
            }
            fixCompass(player);
            player.sendMessage(color(msg("fixed")));
            return true;
        }

        if (name.equals("menu")) {
            if (!isLobby(player)) {
                player.sendMessage(color(msg("not-in-lobby")));
                return true;
            }
            openMenu(player);
            return true;
        }

        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        int delay = getConfig().getInt("settings.give-on-join-delay-ticks", 20);
        Bukkit.getScheduler().runTaskLater(this, () -> fixCompass(event.getPlayer()), Math.max(1, delay));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        int delay = getConfig().getInt("settings.give-on-world-change-delay-ticks", 8);
        Bukkit.getScheduler().runTaskLater(this, () -> fixCompass(event.getPlayer()), Math.max(1, delay));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> fixCompass(event.getPlayer()), 10L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isMenuCompass(item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!isLobby(player)) {
            fixCompass(player);
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> openMenu(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isMenuCompass(event.getItemDrop().getItemStack())) return;
        if (isLobby(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof CompassMenuHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            String action = getMenuAction(clicked);
            if (action == null || action.isBlank()) return;
            executeMenuAction(player, action, clicked);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (isMenuCompass(clicked)) {
            event.setCancelled(true);
            if (!isLobby(player)) {
                fixCompass(player);
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> openMenu(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CompassMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void executeMenuAction(Player player, String command, ItemStack clicked) {
        String sound = getStringFromPdc(clicked, "sound");
        if (sound != null && !sound.isBlank()) playSound(player, sound, 0.8f, 1.1f);

        boolean close = true;
        String closeRaw = getStringFromPdc(clicked, "close");
        if (closeRaw != null) close = Boolean.parseBoolean(closeRaw);
        if (close) player.closeInventory();

        String finalCommand = command.startsWith("/") ? command.substring(1) : command;
        Bukkit.getScheduler().runTask(this, () -> player.performCommand(finalCommand.replace("%player%", player.getName())));
    }

    private void openMenu(Player player) {
        int size = normalizeSize(getConfig().getInt("menu.size", 27));
        Inventory inv = Bukkit.createInventory(new CompassMenuHolder(), size, color(getConfig().getString("menu.title", "&6◆ &e&lMSURVIVAL &7» &fMENU")));

        if (getConfig().getBoolean("menu.filler.enabled", true)) {
            ItemStack filler = basicItem(
                    material(getConfig().getString("menu.filler.material", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE),
                    getConfig().getString("menu.filler.name", "&8"),
                    List.of()
            );
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        }

        ConfigurationSection items = getConfig().getConfigurationSection("menu.items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection sec = items.getConfigurationSection(key);
                if (sec == null) continue;
                int slot = sec.getInt("slot", -1);
                if (slot < 0 || slot >= inv.getSize()) continue;
                ItemStack item = menuItem(sec);
                inv.setItem(slot, item);
            }
        }

        player.openInventory(inv);
    }

    private ItemStack menuItem(ConfigurationSection sec) {
        ItemStack item = basicItem(
                material(sec.getString("material", "STONE"), Material.STONE),
                sec.getString("name", "&fItem"),
                sec.getStringList("lore")
        );
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(menuActionKey, PersistentDataType.STRING, sec.getString("command", ""));
            pdc.set(new NamespacedKey(this, "sound"), PersistentDataType.STRING, sec.getString("sound", ""));
            pdc.set(new NamespacedKey(this, "close"), PersistentDataType.STRING, String.valueOf(sec.getBoolean("close", true)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCompass() {
        ItemStack item = basicItem(
                material(getConfig().getString("compass.material", "COMPASS"), Material.COMPASS),
                getConfig().getString("compass.name", "&6◆ &e&lMSURVIVAL &7» &fMenu"),
                getConfig().getStringList("compass.lore")
        );
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.STRING, "true");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack basicItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name == null ? "" : name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) coloredLore.add(color(line));
            meta.setLore(coloredLore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fixCompass(Player player) {
        if (player == null || !player.isOnline()) return;
        PlayerInventory inv = player.getInventory();
        boolean inLobby = isLobby(player);

        if (!inLobby) {
            if (removeOnNonLobby) removeAllMenuCompasses(inv);
            return;
        }

        removeAllMenuCompasses(inv);
        ItemStack compass = createCompass();
        if (forceSlot) {
            inv.setItem(compassSlot, compass);
        } else {
            ItemStack current = inv.getItem(compassSlot);
            if (current == null || current.getType() == Material.AIR || isMenuCompass(current)) {
                inv.setItem(compassSlot, compass);
            } else {
                int empty = inv.firstEmpty();
                if (empty >= 0) inv.setItem(empty, compass);
            }
        }
        player.updateInventory();
    }

    private void removeAllMenuCompasses(PlayerInventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isMenuCompass(item)) inv.setItem(i, null);
        }
        ItemStack off = inv.getItemInOffHand();
        if (isMenuCompass(off)) inv.setItemInOffHand(null);
    }

    private boolean isMenuCompass(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (meta.getPersistentDataContainer().has(compassKey, PersistentDataType.STRING)) return true;

        if (!cleanupOldByName) return false;
        if (item.getType() != Material.COMPASS) return false;
        if (!meta.hasDisplayName()) return false;
        String stripped = ChatColor.stripColor(meta.getDisplayName());
        if (stripped == null) return false;
        String lower = stripped.toLowerCase(Locale.ROOT);
        for (String part : oldNameContains) {
            if (lower.contains(part)) return true;
        }
        return false;
    }

    private String getMenuAction(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
    }

    private String getStringFromPdc(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(new NamespacedKey(this, key), PersistentDataType.STRING);
    }

    private boolean isLobby(Player player) {
        return player.getWorld().getName().equalsIgnoreCase(lobbyWorld);
    }

    private int normalizeSize(int size) {
        if (size < 9) return 9;
        if (size > 54) return 54;
        return ((size + 8) / 9) * 9;
    }

    private Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        Material mat = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return mat == null ? fallback : mat;
    }

    private String msg(String path) {
        return getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + path, path);
    }

    private String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void playSound(Player player, String raw, float volume, float pitch) {
        if (raw == null || raw.isBlank()) return;
        try {
            Sound sound = Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            // Zly dzwiek w configu nie moze wysadzic pluginu.
        }
    }

    private static final class CompassMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
