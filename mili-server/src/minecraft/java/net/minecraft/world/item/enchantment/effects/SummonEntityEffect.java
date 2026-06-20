package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record SummonEntityEffect(HolderSet<EntityType<?>> entityTypes, boolean joinTeam) implements EnchantmentEntityEffect {
    public static final MapCodec<SummonEntityEffect> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE).fieldOf("entity").forGetter(SummonEntityEffect::entityTypes),
                Codec.BOOL.optionalFieldOf("join_team", false).forGetter(SummonEntityEffect::joinTeam)
            )
            .apply(instance, SummonEntityEffect::new)
    );

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        BlockPos blockPos = BlockPos.containing(origin);
        if (Level.isInSpawnableBounds(blockPos)) {
            Optional<Holder<EntityType<?>>> randomElement = this.entityTypes().getRandomElement(level.getRandom());
            if (!randomElement.isEmpty()) {
                Entity entity1 = randomElement.get().value().create(level, null, blockPos, EntitySpawnReason.TRIGGERED, false, false); // CraftBukkit
                if (entity1 != null) {
                    if (entity1 instanceof LightningBolt lightningBolt && item.owner() instanceof ServerPlayer serverPlayer) {
                        lightningBolt.setCause(serverPlayer);
                    }
                    // CraftBukkit start
                    if (entity1 instanceof LightningBolt) {
                        level.strikeLightning(entity1, (item.itemStack().is(net.minecraft.world.item.Items.TRIDENT)) ? org.bukkit.event.weather.LightningStrikeEvent.Cause.TRIDENT : org.bukkit.event.weather.LightningStrikeEvent.Cause.ENCHANTMENT);
                    } else {
                        level.addFreshEntityWithPassengers(entity1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.ENCHANTMENT);
                    }
                    // CraftBukkit end

                    if (this.joinTeam && entity.getTeam() != null) {
                        level.getScoreboard().addPlayerToTeam(entity1.getScoreboardName(), entity.getTeam());
                    }

                    entity1.snapTo(origin.x, origin.y, origin.z, entity1.getYRot(), entity1.getXRot());
                }
            }
        }
    }

    @Override
    public MapCodec<SummonEntityEffect> codec() {
        return CODEC;
    }
}
