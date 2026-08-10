package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SaplingBlock extends VegetationBlock implements BonemealableBlock {
    public static final MapCodec<SaplingBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(TreeGrower.CODEC.fieldOf("tree").forGetter(saplingBlock -> saplingBlock.treeGrower), propertiesCodec())
            .apply(instance, SaplingBlock::new)
    );
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;
    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 12.0);
    protected final TreeGrower treeGrower;
    public static final ThreadLocal<org.bukkit.TreeType> treeTypeRT = new ThreadLocal<>(); // CraftBukkit // Folia - region threading

    @Override
    public MapCodec<? extends SaplingBlock> codec() {
        return CODEC;
    }

    protected SaplingBlock(TreeGrower treeGrower, BlockBehaviour.Properties properties) {
        super(properties);
        this.treeGrower = treeGrower;
        this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getMaxLocalRawBrightness(pos.above()) >= 9 && random.nextFloat() < (level.spigotConfig.saplingModifier / (100.0F * 7))) { // Spigot - SPIGOT-7159: Better modifier resolution
            this.advanceTree(level, pos, state, random);
        }
    }

    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), Block.UPDATE_NONE);
        } else {
            // CraftBukkit start
            io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
            if (worldData.captureTreeGeneration) { // Folia - region threading
                this.treeGrower.growTree(level, level.getChunkSource().getGenerator(), pos, state, random);
            } else {
                worldData.captureTreeGeneration = true; // Folia - region threading
                this.treeGrower.growTree(level, level.getChunkSource().getGenerator(), pos, state, random);
                worldData.captureTreeGeneration = false; // Folia - region threading
                if (!worldData.capturedBlockStates.isEmpty()) { // Folia - region threading
                    org.bukkit.TreeType treeType = SaplingBlock.treeTypeRT.get(); // Folia - region threading
                    SaplingBlock.treeTypeRT.set(null); // Folia - region threading
                    org.bukkit.Location location = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(pos, level);
                    java.util.List<org.bukkit.block.BlockState> blocks = new java.util.ArrayList<>(worldData.capturedBlockStates.values()); // Folia - region threading
                    worldData.capturedBlockStates.clear(); // Folia - region threading
                    org.bukkit.event.world.StructureGrowEvent event = null;
                    if (treeType != null) {
                        event = new org.bukkit.event.world.StructureGrowEvent(location, treeType, false, null, blocks);
                        org.bukkit.Bukkit.getPluginManager().callEvent(event);
                    }
                    if (event == null || !event.isCancelled()) {
                        for (org.bukkit.block.BlockState blockstate : blocks) {
                            org.bukkit.craftbukkit.block.CraftBlockState craftBlockState = (org.bukkit.craftbukkit.block.CraftBlockState) blockstate;
                            craftBlockState.place(craftBlockState.getFlags());
                            level.checkCapturedTreeStateForObserverNotify(pos, craftBlockState); // Paper - notify observers even if grow failed
                        }
                    }
                }
            }
            // CraftBukkit end
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return level.random.nextFloat() < 0.45;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        this.advanceTree(level, pos, state, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }
}
