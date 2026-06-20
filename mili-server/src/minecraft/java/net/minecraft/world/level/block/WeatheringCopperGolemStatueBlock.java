package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class WeatheringCopperGolemStatueBlock extends CopperGolemStatueBlock implements WeatheringCopper {
    public static final MapCodec<WeatheringCopperGolemStatueBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(ChangeOverTimeBlock::getAge), propertiesCodec())
            .apply(instance, WeatheringCopperGolemStatueBlock::new)
    );

    @Override
    public MapCodec<WeatheringCopperGolemStatueBlock> codec() {
        return CODEC;
    }

    public WeatheringCopperGolemStatueBlock(WeatheringCopper.WeatherState weatheringState, BlockBehaviour.Properties properties) {
        super(weatheringState, properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return WeatheringCopper.getNext(state.getBlock()).isPresent();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.changeOverTime(state, level, pos, random);
    }

    @Override
    public WeatheringCopper.WeatherState getAge() {
        return this.getWeatheringState();
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof CopperGolemStatueBlockEntity copperGolemStatueBlockEntity) {
            if (!stack.is(ItemTags.AXES)) {
                if (stack.is(Items.HONEYCOMB)) {
                    return InteractionResult.PASS;
                }

                return this.updatePose(level, state, pos, player); // Paper - call EntityChangeBlockEvent
            }

            if (this.getAge().equals(WeatheringCopper.WeatherState.UNAFFECTED)) {
                CopperGolem copperGolem = copperGolemStatueBlockEntity.removeStatue(state);
                if (copperGolem != null) {
                    // Paper start - call EntityChangeBlockEvent and spawnReason
                    BlockState newState = level.getFluidState(pos).createLegacyBlock();
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(player, pos, newState)) {
                        return InteractionResult.PASS;
                    }
                    stack.hurtAndBreak(1, player, hand.asEquipmentSlot()); // Paper - moved after event
                    level.addFreshEntity(copperGolem, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REANIMATE); // Paper - add SpawnReason
                    level.setBlock(pos, newState, Block.UPDATE_ALL);
                    // Paper end - call EntityChangeBlockEvent and spawnReason
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.PASS;
    }
}
