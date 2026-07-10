package fun.bm.mili.villager;

import fun.bm.mili.config.modules.optimizations.VillagerOptimizerConfig;
import fun.bm.mili.utils.TPSTracker;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced villager AI optimizer combining LaggRemover and VillagerLobotomizer features.
 * Disables AI for trapped villagers while preserving trading functionality.
 */
public final class VillagerOptimizer implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static VillagerOptimizer instance;

    private final Plugin plugin;
    private final NamespacedKey lobotomizedKey;
    private final NamespacedKey wakeByCommandKey;
    private final NamespacedKey forceLobotomizedKey;
    private final NamespacedKey lastRestockCheckDayTimeKey;
    private final NamespacedKey restocksTodayKey;

    private final Set<Villager> activeVillagers = ConcurrentHashMap.newKeySet();
    private final Set<Villager> inactiveVillagers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> villagerTaskIntervals = new ConcurrentHashMap<>();
    private final Map<Chunk, Long> changedChunks = new ConcurrentHashMap<>();
    private final VillagerActivityPolicy activityPolicy;
    private final BlockClassifier blockClassifier;

    private volatile boolean shuttingDown = false;
    private long lastProcessTime = 0;

    private VillagerOptimizer(Plugin plugin) {
        this.plugin = plugin;
        Plugin activePlugin = getPlugin();
        this.lobotomizedKey = new NamespacedKey(activePlugin, "mili_lobotomized");
        this.wakeByCommandKey = new NamespacedKey(activePlugin, "mili_wake");
        this.forceLobotomizedKey = new NamespacedKey(activePlugin, "mili_force_lobotomy");
        this.lastRestockCheckDayTimeKey = new NamespacedKey(activePlugin, "mili_last_restock");
        this.restocksTodayKey = new NamespacedKey(activePlugin, "mili_restocks_today");
        this.blockClassifier = BlockClassifier.fromServerRegistry();

        Set<String> exemptNames = new HashSet<>();
        for (String name : VillagerOptimizerConfig.alwaysActiveNames) {
            exemptNames.add(name.toLowerCase());
        }

        this.activityPolicy = new VillagerActivityPolicy(
                VillagerOptimizerConfig.lobotomizePassengers,
                VillagerOptimizerConfig.onlyProfessions,
                VillagerOptimizerConfig.onlyWithExperience,
                VillagerOptimizerConfig.checkRoof,
                VillagerOptimizerConfig.ignoreStuckInDoors,
                VillagerOptimizerConfig.ignoreNonSolidBlocks,
                exemptNames,
                blockClassifier
        );
    }

    public static synchronized void init(Plugin plugin) {
        if (!VillagerOptimizerConfig.enabled || instance != null) return;
        instance = new VillagerOptimizer(plugin);
        Plugin activePlugin = instance.getPlugin();
        Bukkit.getPluginManager().registerEvents(instance, activePlugin);

        // Scan existing villagers
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    instance.addVillager(villager);
                }
            }
        }

        // Start chunk processing task
        new BukkitRunnable() {
            @Override
            public void run() {
                if (instance != null) {
                    instance.processChunks();
                }
            }
        }.runTaskTimer(activePlugin, 5L, 5L);

        activePlugin.getLogger().info("[Mili] VillagerOptimizer initialized");
    }

    private Plugin getPlugin() {
        if (plugin != null) return plugin;
        return Bukkit.getPluginManager().getPlugins()[0];
    }

    public static VillagerOptimizer getInstance() {
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.shuttingDown = true;
            instance = null;
        }
    }

    // ========================================================================
    // Event Handlers
    // ========================================================================

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager villager) {
                addVillager(villager);
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager villager) {
                removeVillager(villager);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        changedChunks.put(event.getBlock().getChunk(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        changedChunks.put(event.getBlock().getChunk(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory merchantInventory)) return;
        if (!(merchantInventory.getMerchant() instanceof Villager villager)) return;
        if (!activeVillagers.contains(villager)) return;

        Player player = (Player) event.getPlayer();
        if (VillagerOptimizerConfig.preventTradingUnlobotomized) {
            event.setCancelled(true);
            player.sendMessage(MINI_MESSAGE.deserialize("<red>你不能与未优化的村民交易！</red> <yellow>此村民需要先被优化。</yellow>"));
        } else {
            player.sendMessage(MINI_MESSAGE.deserialize(
                    "<white>[<gold>村民优化</gold>] </white><green>这个村民未被优化，如果用于交易，建议将其困住以优化性能~</green>"
            ));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!VillagerOptimizerConfig.preventTradingUnlobotomized) return;
        if (!(event.getInventory() instanceof MerchantInventory)) return;
        if (!(event.getInventory().getHolder() instanceof Villager villager)) return;
        if (!activeVillagers.contains(villager)) return;

        if (event.getRawSlot() == 2) {
            event.setCancelled(true);
            player.closeInventory();
            player.sendMessage(MINI_MESSAGE.deserialize("<red>未优化的村民无法完成交易！</red>"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!VillagerOptimizerConfig.preventTradingUnlobotomized) return;
        if (!(event.getInventory() instanceof MerchantInventory)) return;
        if (!(event.getInventory().getHolder() instanceof Villager villager)) return;
        if (!activeVillagers.contains(villager)) return;

        if (event.getRawSlots().contains(2)) {
            event.setCancelled(true);
            player.closeInventory();
            player.sendMessage(MINI_MESSAGE.deserialize("<red>未优化的村民无法完成交易！</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        Player player = event.getPlayer();

        if (!player.isSneaking()) {
            sendVillagerStatus(villager, player);
        }
    }

    // ========================================================================
    // Core Logic
    // ========================================================================

    public void addVillager(Villager villager) {
        if (shuttingDown || !getPlugin().isEnabled()) return;

        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        boolean isForce = pdc.has(forceLobotomizedKey, PersistentDataType.BYTE);
        boolean isWake = pdc.has(wakeByCommandKey, PersistentDataType.BYTE);

        if (isForce) {
            lobotomize(villager);
        } else if (isWake) {
            activate(villager);
        } else {
            boolean wasLobotomized = VillagerOptimizerConfig.persistState &&
                    pdc.has(lobotomizedKey, PersistentDataType.BYTE);
            if (wasLobotomized) {
                lobotomize(villager);
            } else {
                activate(villager);
            }
        }
    }

    public void removeVillager(Villager villager) {
        activeVillagers.remove(villager);
        inactiveVillagers.remove(villager);
    }

    private void activate(Villager villager) {
        villager.setAware(true);
        villager.removePotionEffect(PotionEffectType.WATER_BREATHING);
        if (VillagerOptimizerConfig.silentLobotomized) {
            villager.setSilent(false);
        }
        activeVillagers.add(villager);
        inactiveVillagers.remove(villager);
    }

    private void lobotomize(Villager villager) {
        villager.setAware(false);
        villager.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 0, false, false, false));
        if (VillagerOptimizerConfig.silentLobotomized) {
            villager.setSilent(true);
        }
        activeVillagers.remove(villager);
        inactiveVillagers.add(villager);

        if (VillagerOptimizerConfig.persistState) {
            villager.getPersistentDataContainer().set(lobotomizedKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private void processChunks() {
        if (shuttingDown) return;

        // TPS-based interval scaling
        long checkInterval = VillagerOptimizerConfig.checkInterval;
        if (VillagerOptimizerConfig.tpsScaleEnabled && TPSTracker.getTPS() < VillagerOptimizerConfig.tpsScaleThreshold) {
            checkInterval = (long) (checkInterval * VillagerOptimizerConfig.tpsScaleFactor);
        }

        long now = System.currentTimeMillis();
        if (now - lastProcessTime < checkInterval * 50) { // convert ticks to ms (1 tick = 50ms)
            return;
        }
        lastProcessTime = now;

        // Process changed chunks
        Iterator<Map.Entry<Chunk, Long>> it = changedChunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Chunk, Long> entry = it.next();
            Chunk chunk = entry.getKey();
            if (!chunk.isLoaded()) {
                it.remove();
                continue;
            }

            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Villager villager) {
                    if (inactiveVillagers.contains(villager)) {
                        // Re-check if villager should be active
                        if (shouldBeActive(villager)) {
                            activate(villager);
                        }
                    }
                }
            }
            it.remove();
        }

        // Process active villagers
        for (Villager villager : new ArrayList<>(activeVillagers)) {
            if (!villager.isValid() || villager.isDead()) {
                removeVillager(villager);
                continue;
            }
            if (!shouldBeActive(villager)) {
                lobotomize(villager);
            }
        }

        // Process inactive villagers (restocking)
        for (Villager villager : new ArrayList<>(inactiveVillagers)) {
            if (!villager.isValid() || villager.isDead()) {
                removeVillager(villager);
                continue;
            }
            tryRestock(villager);
        }
    }

    private boolean shouldBeActive(Villager villager) {
        String name = "";
        if (villager.customName() != null) {
            name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(villager.customName()).toLowerCase();
        }

        VillagerState state = new VillagerState(
                name,
                villager.isSwimming(),
                villager.isSleeping(),
                villager.getVehicle() != null,
                villager.getProfession() == Villager.Profession.NONE,
                villager.getVillagerExperience(),
                villager.getLocation().getBlockX(),
                villager.getLocation().getBlockY(),
                villager.getLocation().getBlockZ()
        );

        BlockGrid grid = new BlockGrid(villager.getWorld(), state.blockX(), state.blockY(), state.blockZ(), 1);
        return activityPolicy.shouldBeActive(state, grid);
    }

    private void tryRestock(Villager villager) {
        if (!needsToRestock(villager)) return;
        if (!allowedToRestock(villager)) return;

        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        long lastRestockCheck = pdc.getOrDefault(lastRestockCheckDayTimeKey, PersistentDataType.LONG, 0L);
        long fullTime = villager.getWorld().getFullTime();

        // Check for new day
        if (lastRestockCheck > 0L) {
            long lastDay = lastRestockCheck / 24000L;
            long currentDay = fullTime / 24000L;
            if (currentDay > lastDay) {
                pdc.set(restocksTodayKey, PersistentDataType.INTEGER, 0);
            }
        }

        if (!allowedToRestock(villager)) return;

        // Random restock check
        long interval = VillagerOptimizerConfig.restockInterval;
        long randomRange = VillagerOptimizerConfig.restockRandomRange;
        long lastRestock = pdc.getOrDefault(lastRestockCheckDayTimeKey, PersistentDataType.LONG, 0L);
        long elapsed = System.currentTimeMillis() - lastRestock;

        if (randomRange > 0 && randomRange < interval) {
            long adjustedInterval = interval - (long) (Math.random() * randomRange);
            if (elapsed >= adjustedInterval) {
                doRestock(villager);
            }
        } else if (elapsed >= interval) {
            doRestock(villager);
        }
    }

    private void doRestock(Villager villager) {
        villager.restock();
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        pdc.set(lastRestockCheckDayTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
        int restocksToday = pdc.getOrDefault(restocksTodayKey, PersistentDataType.INTEGER, 0);
        pdc.set(restocksTodayKey, PersistentDataType.INTEGER, restocksToday + 1);

        // Play sound
        Sound sound = getProfessionSound(villager.getProfession());
        if (sound != null) {
            villager.getWorld().playSound(villager.getLocation(), sound, org.bukkit.SoundCategory.NEUTRAL, 1.0f, 1.0f);
        }
    }

    private boolean needsToRestock(Villager villager) {
        for (MerchantRecipe recipe : villager.getRecipes()) {
            if (recipe.getUses() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean allowedToRestock(Villager villager) {
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        int restocksToday = pdc.getOrDefault(restocksTodayKey, PersistentDataType.INTEGER, 0);
        return restocksToday < 2;
    }

    private Sound getProfessionSound(Villager.Profession profession) {
        if (profession == null) return null;
        String name = profession.name();
        return switch (name) {
            case "ARMORER" -> Sound.ENTITY_VILLAGER_WORK_ARMORER;
            case "BUTCHER" -> Sound.ENTITY_VILLAGER_WORK_BUTCHER;
            case "CARTOGRAPHER" -> Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER;
            case "CLERIC" -> Sound.ENTITY_VILLAGER_WORK_CLERIC;
            case "FARMER" -> Sound.ENTITY_VILLAGER_WORK_FARMER;
            case "FISHERMAN" -> Sound.ENTITY_VILLAGER_WORK_FISHERMAN;
            case "FLETCHER" -> Sound.ENTITY_VILLAGER_WORK_FLETCHER;
            case "LEATHERWORKER" -> Sound.ENTITY_VILLAGER_WORK_LEATHERWORKER;
            case "LIBRARIAN" -> Sound.ENTITY_VILLAGER_WORK_LIBRARIAN;
            case "MASON" -> Sound.ENTITY_VILLAGER_WORK_MASON;
            case "SHEPHERD" -> Sound.ENTITY_VILLAGER_WORK_SHEPHERD;
            case "TOOLSMITH" -> Sound.ENTITY_VILLAGER_WORK_TOOLSMITH;
            case "WEAPONSMITH" -> Sound.ENTITY_VILLAGER_WORK_WEAPONSMITH;
            default -> null;
        };
    }

    private void sendVillagerStatus(Villager villager, Player player) {
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        boolean isWake = pdc.has(wakeByCommandKey, PersistentDataType.BYTE);
        boolean isForce = pdc.has(forceLobotomizedKey, PersistentDataType.BYTE);

        String statusText;
        if (isWake) {
            statusText = "<green>村民已被唤醒，拥有AI，可以刷新职业。</green><red>再次执行/lobotomy toggle可重新优化</red>";
        } else if (isForce) {
            statusText = "<green>村民已被优化，失去AI，交易仍然可用。</green><red>再次执行/lobotomy toggle可解除优化</red>";
        } else {
            boolean isAware = villager.isAware();
            if (isAware) {
                statusText = "<green>村民未优化，拥有AI，可用于繁殖/刷铁/农业。</green><red>用于交易建议将其困住，节省性能</red>";
            } else {
                statusText = "<red>村民已优化，失去AI，但可以交易。</red><green>用于繁殖/刷铁/农业，建议命名为命/1。</green>";
            }
        }
        player.sendActionBar(MINI_MESSAGE.deserialize(statusText));
    }

    // ========================================================================
    // Public API
    // ========================================================================

    public Set<Villager> getActiveVillagers() {
        return Collections.unmodifiableSet(activeVillagers);
    }

    public Set<Villager> getInactiveVillagers() {
        return Collections.unmodifiableSet(inactiveVillagers);
    }

    public void toggleLobotomy(Villager villager) {
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        if (inactiveVillagers.contains(villager)) {
            // Activate
            pdc.remove(lobotomizedKey);
            pdc.remove(forceLobotomizedKey);
            pdc.set(wakeByCommandKey, PersistentDataType.BYTE, (byte) 1);
            activate(villager);
        } else {
            // Lobotomize
            pdc.remove(wakeByCommandKey);
            pdc.set(forceLobotomizedKey, PersistentDataType.BYTE, (byte) 1);
            lobotomize(villager);
        }
    }
}