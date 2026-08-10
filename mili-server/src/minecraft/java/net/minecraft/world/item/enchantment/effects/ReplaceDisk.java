package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.phys.Vec3;

public record ReplaceDisk(
    LevelBasedValue radius,
    LevelBasedValue height,
    Vec3i offset,
    Optional<BlockPredicate> predicate,
    BlockStateProvider blockState,
    Optional<Holder<GameEvent>> triggerGameEvent
) implements EnchantmentEntityEffect {
    public static final MapCodec<ReplaceDisk> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                LevelBasedValue.CODEC.fieldOf("radius").forGetter(ReplaceDisk::radius),
                LevelBasedValue.CODEC.fieldOf("height").forGetter(ReplaceDisk::height),
                Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO).forGetter(ReplaceDisk::offset),
                BlockPredicate.CODEC.optionalFieldOf("predicate").forGetter(ReplaceDisk::predicate),
                BlockStateProvider.CODEC.fieldOf("block_state").forGetter(ReplaceDisk::blockState),
                GameEvent.CODEC.optionalFieldOf("trigger_game_event").forGetter(ReplaceDisk::triggerGameEvent)
            )
            .apply(instance, ReplaceDisk::new)
    );

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        BlockPos blockPos = BlockPos.containing(origin).offset(this.offset);
        RandomSource random = entity.getRandom();
        int i = (int)this.radius.calculate(enchantmentLevel);
        int i1 = (int)this.height.calculate(enchantmentLevel);

        for (BlockPos blockPos1 : BlockPos.betweenClosed(blockPos.offset(-i, 0, -i), blockPos.offset(i, Math.min(i1 - 1, 0), i))) {
            if (blockPos1.distToCenterSqr(origin.x(), blockPos1.getY() + 0.5, origin.z()) < Mth.square(i)
                && this.predicate.map(predicate -> predicate.test(level, blockPos1)).orElse(true)
                && org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(level, blockPos1, this.blockState.getState(random, blockPos1), net.minecraft.world.level.block.Block.UPDATE_ALL, entity, true)) { // CraftBukkit - Call EntityBlockFormEvent for Frost Walker
                this.triggerGameEvent.ifPresent(event -> level.gameEvent(entity, (Holder<GameEvent>)event, blockPos1));
            }
        }
    }

    @Override
    public MapCodec<ReplaceDisk> codec() {
        return CODEC;
    }
}
