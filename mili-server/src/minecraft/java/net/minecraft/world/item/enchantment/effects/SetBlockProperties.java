package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public record SetBlockProperties(BlockItemStateProperties properties, Vec3i offset, Optional<Holder<GameEvent>> triggerGameEvent)
    implements EnchantmentEntityEffect {
    public static final MapCodec<SetBlockProperties> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                BlockItemStateProperties.CODEC.fieldOf("properties").forGetter(SetBlockProperties::properties),
                Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO).forGetter(SetBlockProperties::offset),
                GameEvent.CODEC.optionalFieldOf("trigger_game_event").forGetter(SetBlockProperties::triggerGameEvent)
            )
            .apply(instance, SetBlockProperties::new)
    );

    public SetBlockProperties(BlockItemStateProperties properties) {
        this(properties, Vec3i.ZERO, Optional.of(GameEvent.BLOCK_CHANGE));
    }

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        BlockPos blockPos = BlockPos.containing(origin).offset(this.offset);
        BlockState blockState = entity.level().getBlockState(blockPos);
        BlockState blockState1 = this.properties.apply(blockState);
        if (blockState != blockState1 && entity.level().setBlock(blockPos, blockState1, Block.UPDATE_ALL)) {
            this.triggerGameEvent.ifPresent(gameEvent -> level.gameEvent(entity, (Holder<GameEvent>)gameEvent, blockPos));
        }
    }

    @Override
    public MapCodec<SetBlockProperties> codec() {
        return CODEC;
    }
}
