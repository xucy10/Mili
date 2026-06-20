package net.minecraft.stats;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public class ServerStatsCounter extends StatsCounter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Map<Stat<?>, Integer>> STATS_CODEC = Codec.dispatchedMap(
            BuiltInRegistries.STAT_TYPE.byNameCodec(), Util.memoize(ServerStatsCounter::createTypedStatsCodec)
        )
        .xmap(map -> {
            Map<Stat<?>, Integer> map1 = new HashMap<>();
            map.forEach((statType, map2) -> map1.putAll((Map<? extends Stat<?>, ? extends Integer>)map2));
            return map1;
        }, map -> map.entrySet().stream().collect(Collectors.groupingBy(entry -> entry.getKey().getType(), Util.toMap())));
    private final Path file;
    private final Set<Stat<?>> dirty = Sets.newHashSet();

    private static <T> Codec<Map<Stat<?>, Integer>> createTypedStatsCodec(StatType<T> type) {
        Codec<T> codec = type.getRegistry().byNameCodec();
        Codec<Stat<?>> codec1 = codec.flatComapMap(
            type::get,
            stat -> stat.getType() == type
                ? DataResult.success((T)stat.getValue())
                : DataResult.error(() -> "Expected type " + type + ", but got " + stat.getType())
        );
        return Codec.unboundedMap(codec1, Codec.INT);
    }

    public ServerStatsCounter(MinecraftServer server, Path file) {
        this.file = file;
        if (Files.isRegularFile(file)) {
            try (Reader bufferedReader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement jsonElement = StrictJsonParser.parse(bufferedReader);
                this.parse(server.getFixerUpper(), jsonElement);
            } catch (IOException var8) {
                LOGGER.error("Couldn't read statistics file {}", file, var8);
            } catch (JsonParseException var9) {
                LOGGER.error("Couldn't parse statistics file {}", file, var9);
            }
        }
        // Paper start - Moved after stat fetching for player state file
        // Moves the loading after vanilla loading, so it overrides the values.
        // Disables saving any forced stats, so it stays at the same value (without enabling disableStatSaving)
        // Fixes stat initialization to not cause a NullPointerException
        // Spigot start
        for (Map.Entry<net.minecraft.resources.Identifier, Integer> entry : org.spigotmc.SpigotConfig.forcedStats.entrySet()) {
            Stat<net.minecraft.resources.Identifier> wrapper = Stats.CUSTOM.get(java.util.Objects.requireNonNull(BuiltInRegistries.CUSTOM_STAT.getValue(entry.getKey()))); // Paper - ensured by SpigotConfig#stats
            this.stats.put(wrapper, entry.getValue().intValue());
        }
        // Spigot end
        // Paper end - Moved after stat fetching for player state file
    }

    public void save() {
        if (org.spigotmc.SpigotConfig.disableStatSaving) return; // Spigot
        try {
            FileUtil.createDirectoriesSafe(this.file.getParent());

            try (Writer bufferedWriter = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8)) {
                GSON.toJson(this.toJson(), GSON.newJsonWriter(bufferedWriter));
            }
        } catch (JsonIOException | IOException var6) {
            LOGGER.error("Couldn't save stats to {}", this.file, var6);
        }
    }

    @Override
    public void setValue(Player player, Stat<?> stat, int value) {
        if (org.spigotmc.SpigotConfig.disableStatSaving) return; // Spigot
        if (stat.getType() == Stats.CUSTOM && stat.getValue() instanceof final net.minecraft.resources.Identifier key && org.spigotmc.SpigotConfig.forcedStats.get(key) != null) return; // Paper - disable saving forced stats
        super.setValue(player, stat, value);
        this.dirty.add(stat);
    }

    private Set<Stat<?>> getDirty() {
        Set<Stat<?>> set = Sets.newHashSet(this.dirty);
        this.dirty.clear();
        return set;
    }

    public void parse(DataFixer fixerUpper, JsonElement json) {
        Dynamic<JsonElement> dynamic = new Dynamic<>(JsonOps.INSTANCE, json);
        dynamic = DataFixTypes.STATS.updateToCurrentVersion(fixerUpper, dynamic, NbtUtils.getDataVersion(dynamic, 1343));
        this.stats
            .putAll(
                STATS_CODEC.parse(dynamic.get("stats").orElseEmptyMap())
                    .resultOrPartial(string -> LOGGER.error("Failed to parse statistics for {}: {}", this.file, string))
                    .orElse(Map.of())
            );
    }

    protected JsonElement toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("stats", STATS_CODEC.encodeStart(JsonOps.INSTANCE, this.stats).getOrThrow());
        jsonObject.addProperty("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        return jsonObject;
    }

    public void markAllDirty() {
        this.dirty.addAll(this.stats.keySet());
    }

    public void sendStats(ServerPlayer player) {
        Object2IntMap<Stat<?>> map = new Object2IntOpenHashMap<>();

        for (Stat<?> stat : this.getDirty()) {
            map.put(stat, this.getValue(stat));
        }

        player.connection.send(new ClientboundAwardStatsPacket(map));
    }
}
