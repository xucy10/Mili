package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class CopperChestBlock extends ChestBlock {
    public static final MapCodec<CopperChestBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(CopperChestBlock::getState),
                BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("open_sound").forGetter(ChestBlock::getOpenChestSound),
                BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("close_sound").forGetter(ChestBlock::getCloseChestSound),
                propertiesCodec()
            )
            .apply(instance, CopperChestBlock::new)
    );
    private static final Map<Block, Supplier<Block>> COPPER_TO_COPPER_CHEST_MAPPING = Map.of(
        Blocks.COPPER_BLOCK,
        () -> Blocks.COPPER_CHEST,
        Blocks.EXPOSED_COPPER,
        () -> Blocks.EXPOSED_COPPER_CHEST,
        Blocks.WEATHERED_COPPER,
        () -> Blocks.WEATHERED_COPPER_CHEST,
        Blocks.OXIDIZED_COPPER,
        () -> Blocks.OXIDIZED_COPPER_CHEST,
        Blocks.WAXED_COPPER_BLOCK,
        () -> Blocks.COPPER_CHEST,
        Blocks.WAXED_EXPOSED_COPPER,
        () -> Blocks.EXPOSED_COPPER_CHEST,
        Blocks.WAXED_WEATHERED_COPPER,
        () -> Blocks.WEATHERED_COPPER_CHEST,
        Blocks.WAXED_OXIDIZED_COPPER,
        () -> Blocks.OXIDIZED_COPPER_CHEST
    );
    private final WeatheringCopper.WeatherState weatherState;

    @Override
    public MapCodec<? extends CopperChestBlock> codec() {
        return CODEC;
    }

    public CopperChestBlock(WeatheringCopper.WeatherState weatherState, SoundEvent openSound, SoundEvent closeSound, BlockBehaviour.Properties properties) {
        super(() -> BlockEntityType.CHEST, openSound, closeSound, properties);
        this.weatherState = weatherState;
    }

    @Override
    public boolean chestCanConnectTo(BlockState state) {
        return state.is(BlockTags.COPPER_CHESTS) && state.hasProperty(ChestBlock.TYPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = super.getStateForPlacement(context);
        return getLeastOxidizedChestOfConnectedBlocks(blockState, context.getLevel(), context.getClickedPos());
    }

    private static BlockState getLeastOxidizedChestOfConnectedBlocks(BlockState state, Level level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.relative(getConnectedDirection(state)));
        if (!state.getValue(ChestBlock.TYPE).equals(ChestType.SINGLE)
            && state.getBlock() instanceof CopperChestBlock copperChestBlock
            && blockState.getBlock() instanceof CopperChestBlock copperChestBlock1) {
            BlockState blockState1 = state;
            BlockState blockState2 = blockState;
            if (copperChestBlock.isWaxed() != copperChestBlock1.isWaxed()) {
                blockState1 = unwaxBlock(copperChestBlock, state).orElse(state);
                blockState2 = unwaxBlock(copperChestBlock1, blockState).orElse(blockState);
            }

            Block block = copperChestBlock.weatherState.ordinal() <= copperChestBlock1.weatherState.ordinal() ? blockState1.getBlock() : blockState2.getBlock();
            return block.withPropertiesOf(blockState1);
        } else {
            return state;
        }
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        BlockState blockState = super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        if (this.chestCanConnectTo(neighborState)) {
            ChestType chestType = blockState.getValue(ChestBlock.TYPE);
            if (!chestType.equals(ChestType.SINGLE) && getConnectedDirection(blockState) == direction) {
                return neighborState.getBlock().withPropertiesOf(blockState);
            }
        }

        return blockState;
    }

    private static Optional<BlockState> unwaxBlock(CopperChestBlock chestBlock, BlockState state) {
        return !chestBlock.isWaxed()
            ? Optional.of(state)
            : Optional.ofNullable(HoneycombItem.WAX_OFF_BY_BLOCK.get().get(state.getBlock())).map(block -> block.withPropertiesOf(state));
    }

    public WeatheringCopper.WeatherState getState() {
        return this.weatherState;
    }

    public static BlockState getFromCopperBlock(Block copperBlock, Direction direction, Level level, BlockPos pos) {
        CopperChestBlock copperChestBlock = (CopperChestBlock)COPPER_TO_COPPER_CHEST_MAPPING.getOrDefault(copperBlock, Blocks.COPPER_CHEST::asBlock).get();
        ChestType chestType = copperChestBlock.getChestType(level, pos, direction);
        BlockState blockState = copperChestBlock.defaultBlockState().setValue(FACING, direction).setValue(TYPE, chestType);
        return getLeastOxidizedChestOfConnectedBlocks(blockState, level, pos);
    }

    public boolean isWaxed() {
        return true;
    }

    @Override
    public boolean shouldChangedStateKeepBlockEntity(BlockState state) {
        return state.is(BlockTags.COPPER_CHESTS);
    }
}
