package net.minecraft.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.FileUtil;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PlayerAdvancements {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create(); // Paper - Remove pretty printing from advancements
    private final PlayerList playerList;
    private final Path playerSavePath;
    private AdvancementTree tree;
    private final Map<AdvancementHolder, AdvancementProgress> progress = new LinkedHashMap<>();
    private final Set<AdvancementHolder> visible = new HashSet<>();
    private final Set<AdvancementHolder> progressChanged = new HashSet<>();
    private final Set<AdvancementNode> rootsToUpdate = new HashSet<>();
    private ServerPlayer player;
    private @Nullable AdvancementHolder lastSelectedTab;
    private boolean isFirstPacket = true;
    private final Codec<PlayerAdvancements.Data> codec;
    public final Map<net.minecraft.advancements.criterion.SimpleCriterionTrigger<?>, Set<CriterionTrigger.Listener<?>>> criterionData = new it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap<>(); // Paper - fix advancement data player leakage // Leaf - Replace criterion map with optimized collection

    public PlayerAdvancements(DataFixer dataFixer, PlayerList playerList, ServerAdvancementManager manager, Path playerSavePath, ServerPlayer player) {
        this.playerList = playerList;
        this.playerSavePath = playerSavePath;
        this.player = player;
        this.tree = manager.tree();
        int i = 1343;
        this.codec = DataFixTypes.ADVANCEMENTS.wrapCodec(PlayerAdvancements.Data.CODEC, dataFixer, 1343);
        this.load(manager);
    }

    public void setPlayer(ServerPlayer player) {
        this.player = player;
    }

    public void stopListening() {
        for (CriterionTrigger<?> criterionTrigger : BuiltInRegistries.TRIGGER_TYPES) {
            criterionTrigger.removePlayerListeners(this);
        }
    }

    public void reload(ServerAdvancementManager manager) {
        this.stopListening();
        this.progress.clear();
        this.visible.clear();
        this.rootsToUpdate.clear();
        this.progressChanged.clear();
        this.isFirstPacket = true;
        this.lastSelectedTab = null;
        this.tree = manager.tree();
        this.load(manager);
    }

    private void registerListeners(ServerAdvancementManager manager) {
        for (AdvancementHolder advancementHolder : manager.getAllAdvancements()) {
            this.registerListeners(advancementHolder);
        }
    }

    private void checkForAutomaticTriggers(ServerAdvancementManager manager) {
        for (AdvancementHolder advancementHolder : manager.getAllAdvancements()) {
            Advancement advancement = advancementHolder.value();
            if (advancement.criteria().isEmpty()) {
                this.award(advancementHolder, "");
                advancement.rewards().grant(this.player);
            }
        }
    }

    private void load(ServerAdvancementManager manager) {
        if (Files.isRegularFile(this.playerSavePath)) {
            try (Reader bufferedReader = Files.newBufferedReader(this.playerSavePath, StandardCharsets.UTF_8)) {
                JsonElement jsonElement = StrictJsonParser.parse(bufferedReader);
                PlayerAdvancements.Data data = this.codec.parse(JsonOps.INSTANCE, jsonElement).getOrThrow(JsonParseException::new);
                this.applyFrom(manager, data);
            } catch (JsonIOException | IOException var7) {
                LOGGER.error("Couldn't access player advancements in {}", this.playerSavePath, var7);
            } catch (JsonParseException var8) {
                LOGGER.error("Couldn't parse player advancements in {}", this.playerSavePath, var8);
            }
        }

        this.checkForAutomaticTriggers(manager);
        this.registerListeners(manager);
    }

    public void save() {
        if (org.spigotmc.SpigotConfig.disableAdvancementSaving) return; // Spigot
        JsonElement jsonElement = this.codec.encodeStart(JsonOps.INSTANCE, this.asData()).getOrThrow();

        try {
            FileUtil.createDirectoriesSafe(this.playerSavePath.getParent());

            try (Writer bufferedWriter = Files.newBufferedWriter(this.playerSavePath, StandardCharsets.UTF_8)) {
                GSON.toJson(jsonElement, GSON.newJsonWriter(bufferedWriter));
            }
        } catch (JsonIOException | IOException var7) {
            LOGGER.error("Couldn't save player advancements to {}", this.playerSavePath, var7);
        }
    }

    private void applyFrom(ServerAdvancementManager advancementManager, PlayerAdvancements.Data data) {
        data.forEach((path, progress) -> {
            AdvancementHolder advancementHolder = advancementManager.get(path);
            if (advancementHolder == null) {
                if (!path.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) return; // CraftBukkit
                LOGGER.warn("Ignored advancement '{}' in progress file {} - it doesn't exist anymore?", path, this.playerSavePath);
            } else {
                this.startProgress(advancementHolder, progress);
                this.progressChanged.add(advancementHolder);
                this.markForVisibilityUpdate(advancementHolder);
            }
        });
    }

    private PlayerAdvancements.Data asData() {
        Map<Identifier, AdvancementProgress> map = new LinkedHashMap<>();
        this.progress.forEach((advancementHolder, progress) -> {
            if (progress.hasProgress()) {
                map.put(advancementHolder.id(), progress);
            }
        });
        return new PlayerAdvancements.Data(map);
    }

    public boolean award(AdvancementHolder advancement, String criterionKey) {
        boolean flag = false;
        AdvancementProgress orStartProgress = this.getOrStartProgress(advancement);
        boolean isDone = orStartProgress.isDone();
        if (orStartProgress.grantProgress(criterionKey)) {
            // Paper start - Add PlayerAdvancementCriterionGrantEvent
            if (!new com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent(this.player.getBukkitEntity(), advancement.toBukkit(), criterionKey).callEvent()) {
                orStartProgress.revokeProgress(criterionKey);
                return false;
            }
            // Paper end - Add PlayerAdvancementCriterionGrantEvent
            this.unregisterListeners(advancement);
            this.progressChanged.add(advancement);
            flag = true;
            if (!isDone && orStartProgress.isDone()) {
                // Paper start - Add Adventure message to PlayerAdvancementDoneEvent
                final net.kyori.adventure.text.Component message = advancement.value().display().flatMap(info -> {
                    return java.util.Optional.ofNullable(
                        info.shouldAnnounceChat() ? io.papermc.paper.adventure.PaperAdventure.asAdventure(info.getType().createAnnouncement(advancement, this.player)) : null
                    );
                }).orElse(null);
                final org.bukkit.event.player.PlayerAdvancementDoneEvent event = new org.bukkit.event.player.PlayerAdvancementDoneEvent(this.player.getBukkitEntity(), advancement.toBukkit(), message);
                this.player.level().getCraftServer().getPluginManager().callEvent(event); // CraftBukkit
                // Paper end
                advancement.value().rewards().grant(this.player);
                advancement.value().display().ifPresent(displayInfo -> {
                    // Paper start - Add Adventure message to PlayerAdvancementDoneEvent
                    if (event.message() != null && this.player.level().getGameRules().get(GameRules.SHOW_ADVANCEMENT_MESSAGES)) {
                        this.playerList.broadcastSystemMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.message()), false);
                        // Paper end
                    }
                });
            }
        }

        if (!isDone && orStartProgress.isDone()) {
            this.markForVisibilityUpdate(advancement);
        }

        return flag;
    }

    public boolean revoke(AdvancementHolder advancement, String criterionKey) {
        boolean flag = false;
        AdvancementProgress orStartProgress = this.getOrStartProgress(advancement);
        boolean isDone = orStartProgress.isDone();
        if (orStartProgress.revokeProgress(criterionKey)) {
            this.registerListeners(advancement);
            this.progressChanged.add(advancement);
            flag = true;
        }

        if (isDone && !orStartProgress.isDone()) {
            this.markForVisibilityUpdate(advancement);
        }

        return flag;
    }

    private void markForVisibilityUpdate(AdvancementHolder advancement) {
        AdvancementNode advancementNode = this.tree.get(advancement);
        if (advancementNode != null) {
            this.rootsToUpdate.add(advancementNode.root());
        }
    }

    private void registerListeners(AdvancementHolder advancement) {
        AdvancementProgress orStartProgress = this.getOrStartProgress(advancement);
        if (!orStartProgress.isDone()) {
            for (Entry<String, Criterion<?>> entry : advancement.value().criteria().entrySet()) {
                CriterionProgress criterion = orStartProgress.getCriterion(entry.getKey());
                if (criterion != null && !criterion.isDone()) {
                    this.registerListener(advancement, entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private <T extends CriterionTriggerInstance> void registerListener(AdvancementHolder advancement, String criterionKey, Criterion<T> criterion) {
        criterion.trigger().addPlayerListener(this, new CriterionTrigger.Listener<>(criterion.triggerInstance(), advancement, criterionKey));
    }

    private void unregisterListeners(AdvancementHolder advancement) {
        AdvancementProgress orStartProgress = this.getOrStartProgress(advancement);

        for (Entry<String, Criterion<?>> entry : advancement.value().criteria().entrySet()) {
            CriterionProgress criterion = orStartProgress.getCriterion(entry.getKey());
            if (criterion != null && (criterion.isDone() || orStartProgress.isDone())) {
                this.removeListener(advancement, entry.getKey(), entry.getValue());
            }
        }
    }

    private <T extends CriterionTriggerInstance> void removeListener(AdvancementHolder advancement, String criterionKey, Criterion<T> criterion) {
        criterion.trigger().removePlayerListener(this, new CriterionTrigger.Listener<>(criterion.triggerInstance(), advancement, criterionKey));
    }

    public void flushDirty(ServerPlayer player, boolean showAdvancements) {
        if (this.isFirstPacket || !this.rootsToUpdate.isEmpty() || !this.progressChanged.isEmpty()) {
            Map<Identifier, AdvancementProgress> map = new HashMap<>();
            Set<AdvancementHolder> set = new java.util.TreeSet<>(java.util.Comparator.comparing(adv -> adv.id().toString())); // Paper - Changed from HashSet to TreeSet ordered alphabetically.
            Set<Identifier> set1 = new HashSet<>();

            for (AdvancementNode advancementNode : this.rootsToUpdate) {
                this.updateTreeVisibility(advancementNode, set, set1);
            }

            this.rootsToUpdate.clear();

            for (AdvancementHolder advancementHolder : this.progressChanged) {
                if (this.visible.contains(advancementHolder)) {
                    map.put(advancementHolder.id(), this.progress.get(advancementHolder));
                }
            }

            this.progressChanged.clear();
            if (!map.isEmpty() || !set.isEmpty() || !set1.isEmpty()) {
                player.connection.send(new ClientboundUpdateAdvancementsPacket(this.isFirstPacket, set, set1, map, showAdvancements));
            }
        }

        this.isFirstPacket = false;
    }

    public void setSelectedTab(@Nullable AdvancementHolder advancement) {
        AdvancementHolder advancementHolder = this.lastSelectedTab;
        if (advancement != null && advancement.value().isRoot() && advancement.value().display().isPresent()) {
            this.lastSelectedTab = advancement;
        } else {
            this.lastSelectedTab = null;
        }

        if (advancementHolder != this.lastSelectedTab) {
            this.player.connection.send(new ClientboundSelectAdvancementsTabPacket(this.lastSelectedTab == null ? null : this.lastSelectedTab.id()));
        }
    }

    public AdvancementProgress getOrStartProgress(AdvancementHolder advancement) {
        AdvancementProgress advancementProgress = this.progress.get(advancement);
        if (advancementProgress == null) {
            advancementProgress = new AdvancementProgress();
            this.startProgress(advancement, advancementProgress);
        }

        return advancementProgress;
    }

    private void startProgress(AdvancementHolder advancement, AdvancementProgress advancementProgress) {
        advancementProgress.update(advancement.value().requirements());
        this.progress.put(advancement, advancementProgress);
    }

    private void updateTreeVisibility(AdvancementNode root, Set<AdvancementHolder> advancementOutput, Set<Identifier> idOutput) {
        AdvancementVisibilityEvaluator.evaluateVisibility(
            root, advancementNode -> this.getOrStartProgress(advancementNode.holder()).isDone(), (node, visible) -> {
                AdvancementHolder advancementHolder = node.holder();
                if (visible) {
                    if (this.visible.add(advancementHolder)) {
                        advancementOutput.add(advancementHolder);
                        if (this.progress.containsKey(advancementHolder)) {
                            this.progressChanged.add(advancementHolder);
                        }
                    }
                } else if (this.visible.remove(advancementHolder)) {
                    idOutput.add(advancementHolder.id());
                }
            }
        );
    }

    record Data(Map<Identifier, AdvancementProgress> map) {
        public static final Codec<PlayerAdvancements.Data> CODEC = Codec.unboundedMap(Identifier.CODEC, AdvancementProgress.CODEC)
            .xmap(PlayerAdvancements.Data::new, PlayerAdvancements.Data::map);

        public void forEach(BiConsumer<Identifier, AdvancementProgress> action) {
            this.map.entrySet().stream().sorted(Entry.comparingByValue()).forEach(entry -> action.accept(entry.getKey(), entry.getValue()));
        }
    }
}
