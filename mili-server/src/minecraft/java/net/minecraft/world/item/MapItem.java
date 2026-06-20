package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

public class MapItem extends Item {
    public static final int IMAGE_WIDTH = 128;
    public static final int IMAGE_HEIGHT = 128;

    public MapItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemStack create(ServerLevel level, int x, int z, byte scale, boolean trackingPosition, boolean unlimitedTracking) {
        ItemStack itemStack = new ItemStack(Items.FILLED_MAP);
        MapId mapId = createNewSavedData(level, x, z, scale, trackingPosition, unlimitedTracking, level.dimension());
        itemStack.set(DataComponents.MAP_ID, mapId);
        return itemStack;
    }

    public static @Nullable MapItemSavedData getSavedData(@Nullable MapId mapId, Level level) {
        return mapId == null ? null : level.getMapData(mapId);
    }

    public static @Nullable MapItemSavedData getSavedData(ItemStack stack, Level level) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        return getSavedData(mapId, level);
    }

    public static MapId createNewSavedData(
        ServerLevel level, int x, int z, int scale, boolean trackingPosition, boolean unlimitedTracking, ResourceKey<Level> dimension
    ) {
        MapItemSavedData mapItemSavedData = MapItemSavedData.createFresh(x, z, (byte)scale, trackingPosition, unlimitedTracking, dimension);
        MapId freeMapId = level.getFreeMapId();
        level.setMapData(freeMapId, mapItemSavedData);
        return freeMapId;
    }

    public void update(Level level, Entity viewer, MapItemSavedData data) {
        synchronized (data) { // Folia - make map data thread-safe
        if (level.dimension() == data.dimension && viewer instanceof Player) {
            int i = 1 << data.scale;
            int i1 = data.centerX;
            int i2 = data.centerZ;
            int i3 = Mth.floor(viewer.getX() - i1) / i + 64;
            int i4 = Mth.floor(viewer.getZ() - i2) / i + 64;
            int i5 = 128 / i;
            if (level.dimensionType().hasCeiling()) {
                i5 /= 2;
            }

            MapItemSavedData.HoldingPlayer holdingPlayer = data.getHoldingPlayer((Player)viewer);
            holdingPlayer.step++;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos mutableBlockPos1 = new BlockPos.MutableBlockPos();
            boolean flag = false;

            for (int i6 = i3 - i5 + 1; i6 < i3 + i5; i6++) {
                if ((i6 & 15) == (holdingPlayer.step & 15) || flag) {
                    flag = false;
                    double d = 0.0;

                    for (int i7 = i4 - i5 - 1; i7 < i4 + i5; i7++) {
                        if (i6 >= 0 && i7 >= -1 && i6 < 128 && i7 < 128) {
                            int i8 = Mth.square(i6 - i3) + Mth.square(i7 - i4);
                            boolean flag1 = i8 > (i5 - 2) * (i5 - 2);
                            int i9 = (i1 / i + i6 - 64) * i;
                            int i10 = (i2 / i + i7 - 64) * i;
                            Multiset<MapColor> multiset = LinkedHashMultiset.create();
                            LevelChunk chunk = level.getChunkIfLoaded(SectionPos.blockToSectionCoord(i9), SectionPos.blockToSectionCoord(i10)); // Paper - Maps shouldn't load chunks // Folia - super important that it uses getChunkIfLoaded
                            if (chunk != null && !chunk.isEmpty() && ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(level, chunk.getPos())) { // Paper - Maps shouldn't load chunks // Folia - make sure chunk is owned
                                int i11 = 0;
                                double d1 = 0.0;
                                if (level.dimensionType().hasCeiling()) {
                                    int i12 = i9 + i10 * 231871;
                                    i12 = i12 * i12 * 31287121 + i12 * 11;
                                    if ((i12 >> 20 & 1) == 0) {
                                        multiset.add(Blocks.DIRT.defaultBlockState().getMapColor(level, BlockPos.ZERO), 10);
                                    } else {
                                        multiset.add(Blocks.STONE.defaultBlockState().getMapColor(level, BlockPos.ZERO), 100);
                                    }

                                    d1 = 100.0;
                                } else {
                                    for (int i12 = 0; i12 < i; i12++) {
                                        for (int i13 = 0; i13 < i; i13++) {
                                            mutableBlockPos.set(i9 + i12, 0, i10 + i13);
                                            int i14 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, mutableBlockPos.getX(), mutableBlockPos.getZ()) + 1;
                                            BlockState blockState;
                                            if (i14 <= level.getMinY()) {
                                                blockState = Blocks.BEDROCK.defaultBlockState();
                                            } else {
                                                do {
                                                    mutableBlockPos.setY(--i14);
                                                    blockState = chunk.getBlockState(mutableBlockPos);
                                                } while (blockState.getMapColor(level, mutableBlockPos) == MapColor.NONE && i14 > level.getMinY());

                                                if (i14 > level.getMinY() && !blockState.getFluidState().isEmpty()) {
                                                    int i15 = i14 - 1;
                                                    mutableBlockPos1.set(mutableBlockPos);

                                                    BlockState blockState1;
                                                    do {
                                                        mutableBlockPos1.setY(i15--);
                                                        blockState1 = chunk.getBlockState(mutableBlockPos1);
                                                        i11++;
                                                    } while (i15 > level.getMinY() && !blockState1.getFluidState().isEmpty());

                                                    blockState = this.getCorrectStateForFluidBlock(level, blockState, mutableBlockPos);
                                                }
                                            }

                                            data.checkBanners(level, mutableBlockPos.getX(), mutableBlockPos.getZ());
                                            d1 += (double)i14 / (i * i);
                                            multiset.add(blockState.getMapColor(level, mutableBlockPos));
                                        }
                                    }
                                }

                                i11 /= i * i;
                                MapColor mapColor = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.NONE);
                                MapColor.Brightness brightness;
                                if (mapColor == MapColor.WATER) {
                                    double d2 = i11 * 0.1 + (i6 + i7 & 1) * 0.2;
                                    if (d2 < 0.5) {
                                        brightness = MapColor.Brightness.HIGH;
                                    } else if (d2 > 0.9) {
                                        brightness = MapColor.Brightness.LOW;
                                    } else {
                                        brightness = MapColor.Brightness.NORMAL;
                                    }
                                } else {
                                    double d2 = (d1 - d) * 4.0 / (i + 4) + ((i6 + i7 & 1) - 0.5) * 0.4;
                                    if (d2 > 0.6) {
                                        brightness = MapColor.Brightness.HIGH;
                                    } else if (d2 < -0.6) {
                                        brightness = MapColor.Brightness.LOW;
                                    } else {
                                        brightness = MapColor.Brightness.NORMAL;
                                    }
                                }

                                d = d1;
                                if (i7 >= 0 && i8 < i5 * i5 && (!flag1 || (i6 + i7 & 1) != 0)) {
                                    flag |= data.updateColor(i6, i7, mapColor.getPackedId(brightness));
                                }
                            }
                        }
                    }
                }
            }
        }
        } // Folia - make map data thread-safe
    }

    private BlockState getCorrectStateForFluidBlock(Level level, BlockState state, BlockPos pos) {
        FluidState fluidState = state.getFluidState();
        return !fluidState.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluidState.createLegacyBlock() : state;
    }

    private static boolean isBiomeWatery(boolean[] wateryMap, int xSample, int zSample) {
        return wateryMap[zSample * 128 + xSample];
    }

    public static void renderBiomePreviewMap(ServerLevel level, ItemStack stack) {
        MapItemSavedData savedData = getSavedData(stack, level);
        if (savedData != null) {
            synchronized (savedData) { // Folia - make map data thread-safe
            if (level.dimension() == savedData.dimension) {
                int i = 1 << savedData.scale;
                int i1 = savedData.centerX;
                int i2 = savedData.centerZ;
                boolean[] flags = new boolean[16384];
                int i3 = i1 / i - 64;
                int i4 = i2 / i - 64;
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

                for (int i5 = 0; i5 < 128; i5++) {
                    for (int i6 = 0; i6 < 128; i6++) {
                        Holder<Biome> biome = level.getUncachedNoiseBiome(net.minecraft.core.QuartPos.fromBlock((i3 + i6) * i), net.minecraft.core.QuartPos.fromBlock(0), net.minecraft.core.QuartPos.fromBlock((i4 + i5) * i)); // Paper - Perf: Use seed based lookup for treasure maps
                        flags[i5 * 128 + i6] = biome.is(BiomeTags.WATER_ON_MAP_OUTLINES);
                    }
                }

                for (int i5 = 1; i5 < 127; i5++) {
                    for (int i6 = 1; i6 < 127; i6++) {
                        int i7 = 0;

                        for (int i8 = -1; i8 < 2; i8++) {
                            for (int i9 = -1; i9 < 2; i9++) {
                                if ((i8 != 0 || i9 != 0) && isBiomeWatery(flags, i5 + i8, i6 + i9)) {
                                    i7++;
                                }
                            }
                        }

                        MapColor.Brightness brightness = MapColor.Brightness.LOWEST;
                        MapColor mapColor = MapColor.NONE;
                        if (isBiomeWatery(flags, i5, i6)) {
                            mapColor = MapColor.COLOR_ORANGE;
                            if (i7 > 7 && i6 % 2 == 0) {
                                switch ((i5 + (int)(Mth.sin(i6 + 0.0F) * 7.0F)) / 8 % 5) {
                                    case 0:
                                    case 4:
                                        brightness = MapColor.Brightness.LOW;
                                        break;
                                    case 1:
                                    case 3:
                                        brightness = MapColor.Brightness.NORMAL;
                                        break;
                                    case 2:
                                        brightness = MapColor.Brightness.HIGH;
                                }
                            } else if (i7 > 7) {
                                mapColor = MapColor.NONE;
                            } else if (i7 > 5) {
                                brightness = MapColor.Brightness.NORMAL;
                            } else if (i7 > 3) {
                                brightness = MapColor.Brightness.LOW;
                            } else if (i7 > 1) {
                                brightness = MapColor.Brightness.LOW;
                            }
                        } else if (i7 > 0) {
                            mapColor = MapColor.COLOR_BROWN;
                            if (i7 > 3) {
                                brightness = MapColor.Brightness.NORMAL;
                            } else {
                                brightness = MapColor.Brightness.LOWEST;
                            }
                        }

                        if (mapColor != MapColor.NONE) {
                            savedData.setColor(i5, i6, mapColor.getPackedId(brightness));
                        }
                    }
                }
            }
            } // Folia - make map data thread-safe
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        MapItemSavedData savedData = getSavedData(stack, level);
        if (savedData != null) {
            synchronized (savedData) { // Folia - region threading
            if (entity instanceof Player player) {
                savedData.tickCarriedBy(player, stack);
            }

            if (!savedData.locked && slot != null && slot.getType() == EquipmentSlot.Type.HAND) {
                this.update(level, entity, savedData);
            }
            } // Folia - region threading
        }
    }

    @Override
    public void onCraftedPostProcess(ItemStack stack, Level level) {
        MapPostProcessing mapPostProcessing = stack.remove(DataComponents.MAP_POST_PROCESSING);
        if (mapPostProcessing != null) {
            if (level instanceof ServerLevel serverLevel) {
                switch (mapPostProcessing) {
                    case LOCK:
                        lockMap(stack, serverLevel);
                        break;
                    case SCALE:
                        scaleMap(stack, serverLevel);
                }
            }
        }
    }

    private static void scaleMap(ItemStack stack, ServerLevel level) {
        MapItemSavedData savedData = getSavedData(stack, level);
        if (savedData != null) {
            MapId freeMapId = level.getFreeMapId();
            level.setMapData(freeMapId, savedData.scaled());
            stack.set(DataComponents.MAP_ID, freeMapId);
        }
    }

    private static void lockMap(ItemStack stack, ServerLevel level) {
        MapItemSavedData savedData = getSavedData(stack, level);
        if (savedData != null) {
            MapId freeMapId = level.getFreeMapId();
            MapItemSavedData mapItemSavedData = savedData.locked();
            level.setMapData(freeMapId, mapItemSavedData);
            stack.set(DataComponents.MAP_ID, freeMapId);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        if (blockState.is(BlockTags.BANNERS)) {
            if (!context.getLevel().isClientSide()) {
                MapItemSavedData savedData = getSavedData(context.getItemInHand(), context.getLevel());
                if (savedData != null && !savedData.toggleBanner(context.getLevel(), context.getClickedPos())) {
                    return InteractionResult.FAIL;
                }
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.useOn(context);
        }
    }
}
