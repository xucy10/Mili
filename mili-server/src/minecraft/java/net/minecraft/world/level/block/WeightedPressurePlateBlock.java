package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class WeightedPressurePlateBlock extends BasePressurePlateBlock {
    public static final MapCodec<WeightedPressurePlateBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.intRange(1, 1024).fieldOf("max_weight").forGetter(weightedPressurePlateBlock -> weightedPressurePlateBlock.maxWeight),
                BlockSetType.CODEC.fieldOf("block_set_type").forGetter(weightedPressurePlateBlock -> weightedPressurePlateBlock.type),
                propertiesCodec()
            )
            .apply(instance, WeightedPressurePlateBlock::new)
    );
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private final int maxWeight;

    @Override
    public MapCodec<WeightedPressurePlateBlock> codec() {
        return CODEC;
    }

    protected WeightedPressurePlateBlock(int maxWeight, BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties, type);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
        this.maxWeight = maxWeight;
    }

    @Override
    protected int getSignalStrength(Level level, BlockPos pos) {
        // CraftBukkit start
        // int min = Math.min(getEntityCount(level, TOUCH_AABB.move(pos), Entity.class), this.maxWeight);
        int min = 0;
        for (Entity entity : getEntities(level, TOUCH_AABB.move(pos), Entity.class)) {
            org.bukkit.event.Cancellable cancellable;

            if (entity instanceof net.minecraft.world.entity.player.Player player) {
                cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(player, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
            } else {
                cancellable = new org.bukkit.event.entity.EntityInteractEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos));
                level.getCraftServer().getPluginManager().callEvent((org.bukkit.event.entity.EntityInteractEvent) cancellable);
            }

            // We only want to block turning the plate on if all events are cancelled
            if (!cancellable.isCancelled()) {
                min++;
            }
        }
        // CraftBukkit end
        if (min > 0) {
            float f = (float)Math.min(this.maxWeight, min) / this.maxWeight;
            return Mth.ceil(f * 15.0F);
        } else {
            return 0;
        }
    }

    @Override
    protected int getSignalForState(BlockState state) {
        return state.getValue(POWER);
    }

    @Override
    protected BlockState setSignalForState(BlockState state, int strength) {
        return state.setValue(POWER, strength);
    }

    @Override
    protected int getPressedTime() {
        return 10;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }
}
