package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gamerules.GameRules;

public class InfestedBlock extends Block {
    public static final MapCodec<InfestedBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("host").forGetter(InfestedBlock::getHostBlock), propertiesCodec())
            .apply(instance, InfestedBlock::new)
    );
    private final Block hostBlock;
    private static final Map<Block, Block> BLOCK_BY_HOST_BLOCK = Maps.newIdentityHashMap();
    private static final Map<BlockState, BlockState> HOST_TO_INFESTED_STATES = Maps.newIdentityHashMap();
    private static final Map<BlockState, BlockState> INFESTED_TO_HOST_STATES = Maps.newIdentityHashMap();

    @Override
    public MapCodec<? extends InfestedBlock> codec() {
        return CODEC;
    }

    public InfestedBlock(Block hostBlock, BlockBehaviour.Properties properties) {
        super(properties.destroyTime(hostBlock.defaultDestroyTime() / 2.0F).explosionResistance(0.75F));
        this.hostBlock = hostBlock;
        BLOCK_BY_HOST_BLOCK.put(hostBlock, this);
    }

    public Block getHostBlock() {
        return this.hostBlock;
    }

    public static boolean isCompatibleHostBlock(BlockState state) {
        return BLOCK_BY_HOST_BLOCK.containsKey(state.getBlock());
    }

    private void spawnInfestation(ServerLevel level, BlockPos pos) {
        Silverfish silverfish = EntityType.SILVERFISH.create(level, EntitySpawnReason.TRIGGERED);
        if (silverfish != null) {
            silverfish.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            level.addFreshEntity(silverfish, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SILVERFISH_BLOCK); // CraftBukkit - add SpawnReason
            silverfish.spawnAnim();
        }
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, stack, dropExperience);
        if (level.getGameRules().get(GameRules.BLOCK_DROPS) && !EnchantmentHelper.hasTag(stack, EnchantmentTags.PREVENTS_INFESTED_SPAWNS)) {
            this.spawnInfestation(level, pos);
        }
    }

    public static BlockState infestedStateByHost(BlockState host) {
        return getNewStateWithProperties(HOST_TO_INFESTED_STATES, host, () -> BLOCK_BY_HOST_BLOCK.get(host.getBlock()).defaultBlockState());
    }

    public BlockState hostStateByInfested(BlockState infested) {
        return getNewStateWithProperties(INFESTED_TO_HOST_STATES, infested, () -> this.getHostBlock().defaultBlockState());
    }

    private static BlockState getNewStateWithProperties(Map<BlockState, BlockState> stateMap, BlockState state, Supplier<BlockState> supplier) {
        return stateMap.computeIfAbsent(state, blockState -> {
            BlockState blockState1 = supplier.get();

            for (Property property : blockState.getProperties()) {
                blockState1 = blockState1.hasProperty(property) ? blockState1.setValue(property, blockState.getValue(property)) : blockState1;
            }

            return blockState1;
        });
    }
}
