package net.minecraft.server.bossevents;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CustomBossEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Map<Identifier, CustomBossEvent.Packed>> EVENTS_CODEC = Codec.unboundedMap(Identifier.CODEC, CustomBossEvent.Packed.CODEC);
    private final Map<Identifier, CustomBossEvent> events = Maps.newHashMap();

    public @Nullable CustomBossEvent get(Identifier id) {
        return this.events.get(id);
    }

    public CustomBossEvent create(Identifier id, Component name) {
        CustomBossEvent customBossEvent = new CustomBossEvent(id, name);
        this.events.put(id, customBossEvent);
        return customBossEvent;
    }

    public void remove(CustomBossEvent bossbar) {
        this.events.remove(bossbar.getTextId());
    }

    public Collection<Identifier> getIds() {
        return this.events.keySet();
    }

    public Collection<CustomBossEvent> getEvents() {
        return this.events.values();
    }

    public CompoundTag save(HolderLookup.Provider levelRegistry) {
        Map<Identifier, CustomBossEvent.Packed> map = Util.mapValues(this.events, CustomBossEvent::pack);
        return (CompoundTag)EVENTS_CODEC.encodeStart(levelRegistry.createSerializationContext(NbtOps.INSTANCE), map).getOrThrow();
    }

    public void load(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        Map<Identifier, CustomBossEvent.Packed> map = EVENTS_CODEC.parse(levelRegistry.createSerializationContext(NbtOps.INSTANCE), tag)
            .resultOrPartial(string -> LOGGER.error("Failed to parse boss bar events: {}", string))
            .orElse(Map.of());
        map.forEach((identifier, packed) -> this.events.put(identifier, CustomBossEvent.load(identifier, packed)));
    }

    public void onPlayerConnect(ServerPlayer player) {
        for (CustomBossEvent customBossEvent : this.events.values()) {
            customBossEvent.onPlayerConnect(player);
        }
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        for (CustomBossEvent customBossEvent : this.events.values()) {
            customBossEvent.onPlayerDisconnect(player);
        }
    }
}
