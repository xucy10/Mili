package net.minecraft.gametest.framework;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class StructureUtils {
    public static final int DEFAULT_Y_SEARCH_RADIUS = 10;
    public static final String DEFAULT_TEST_STRUCTURES_DIR = "Minecraft.Server/src/test/convertables/data";
    public static Path testStructuresDir = Paths.get("Minecraft.Server/src/test/convertables/data");

    public static Rotation getRotationForRotationSteps(int rotationSteps) {
        switch (rotationSteps) {
            case 0:
                return Rotation.NONE;
            case 1:
                return Rotation.CLOCKWISE_90;
            case 2:
                return Rotation.CLOCKWISE_180;
            case 3:
                return Rotation.COUNTERCLOCKWISE_90;
            default:
                throw new IllegalArgumentException("rotationSteps must be a value from 0-3. Got value " + rotationSteps);
        }
    }

    public static int getRotationStepsForRotation(Rotation rotation) {
        switch (rotation) {
            case NONE:
                return 0;
            case CLOCKWISE_90:
                return 1;
            case CLOCKWISE_180:
                return 2;
            case COUNTERCLOCKWISE_90:
                return 3;
            default:
                throw new IllegalArgumentException("Unknown rotation value, don't know how many steps it represents: " + rotation);
        }
    }

    public static TestInstanceBlockEntity createNewEmptyTest(Identifier id, BlockPos pos, Vec3i size, Rotation rotation, ServerLevel level) {
        BoundingBox structureBoundingBox = getStructureBoundingBox(TestInstanceBlockEntity.getStructurePos(pos), size, rotation);
        clearSpaceForStructure(structureBoundingBox, level);
        level.setBlockAndUpdate(pos, Blocks.TEST_INSTANCE_BLOCK.defaultBlockState());
        TestInstanceBlockEntity testInstanceBlockEntity = (TestInstanceBlockEntity)level.getBlockEntity(pos);
        ResourceKey<GameTestInstance> resourceKey = ResourceKey.create(Registries.TEST_INSTANCE, id);
        testInstanceBlockEntity.set(
            new TestInstanceBlockEntity.Data(Optional.of(resourceKey), size, rotation, false, TestInstanceBlockEntity.Status.CLEARED, Optional.empty())
        );
        return testInstanceBlockEntity;
    }

    public static void clearSpaceForStructure(BoundingBox boundingBox, ServerLevel level) {
        int i = boundingBox.minY() - 1;
        BlockPos.betweenClosedStream(boundingBox).forEach(pos -> clearBlock(i, pos, level));
        level.getBlockTicks().clearArea(boundingBox);
        level.clearBlockEvents(boundingBox);
        AABB aabb = AABB.of(boundingBox);
        List<Entity> entitiesOfClass = level.getEntitiesOfClass(Entity.class, aabb, entity -> !(entity instanceof Player));
        entitiesOfClass.forEach(entity -> entity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD)); // Paper
    }

    public static BlockPos getTransformedFarCorner(BlockPos pos, Vec3i offset, Rotation rotation) {
        BlockPos blockPos = pos.offset(offset).offset(-1, -1, -1);
        return StructureTemplate.transform(blockPos, Mirror.NONE, rotation, pos);
    }

    public static BoundingBox getStructureBoundingBox(BlockPos pos, Vec3i offset, Rotation rotation) {
        BlockPos transformedFarCorner = getTransformedFarCorner(pos, offset, rotation);
        BoundingBox boundingBox = BoundingBox.fromCorners(pos, transformedFarCorner);
        int min = Math.min(boundingBox.minX(), boundingBox.maxX());
        int min1 = Math.min(boundingBox.minZ(), boundingBox.maxZ());
        return boundingBox.move(pos.getX() - min, 0, pos.getZ() - min1);
    }

    public static Optional<BlockPos> findTestContainingPos(BlockPos pos, int radius, ServerLevel level) {
        return findTestBlocks(pos, radius, level).filter(pos1 -> doesStructureContain(pos1, pos, level)).findFirst();
    }

    public static Optional<BlockPos> findNearestTest(BlockPos pos, int radius, ServerLevel level) {
        Comparator<BlockPos> comparator = Comparator.comparingInt(pos1 -> pos1.distManhattan(pos));
        return findTestBlocks(pos, radius, level).min(comparator);
    }

    public static Stream<BlockPos> findTestBlocks(BlockPos pos, int radius, ServerLevel level) {
        return level.getPoiManager()
            .findAll(holder -> holder.is(PoiTypes.TEST_INSTANCE), blockPos -> true, pos, radius, PoiManager.Occupancy.ANY)
            .map(BlockPos::immutable);
    }

    public static Stream<BlockPos> lookedAtTestPos(BlockPos pos, Entity entity, ServerLevel level) {
        int i = 250;
        Vec3 eyePosition = entity.getEyePosition();
        Vec3 vec3 = eyePosition.add(entity.getLookAngle().scale(250.0));
        return findTestBlocks(pos, 250, level)
            .map(blockPos -> level.getBlockEntity(blockPos, BlockEntityType.TEST_INSTANCE_BLOCK))
            .flatMap(Optional::stream)
            .filter(testInstanceBlockEntity -> testInstanceBlockEntity.getStructureBounds().clip(eyePosition, vec3).isPresent())
            .map(BlockEntity::getBlockPos)
            .sorted(Comparator.comparing(pos::distSqr))
            .limit(1L);
    }

    private static void clearBlock(int structureBlockY, BlockPos pos, ServerLevel level) {
        BlockState blockState;
        if (pos.getY() < structureBlockY) {
            blockState = Blocks.STONE.defaultBlockState();
        } else {
            blockState = Blocks.AIR.defaultBlockState();
        }

        BlockInput blockInput = new BlockInput(blockState, Collections.emptySet(), null);
        blockInput.place(level, pos, Block.UPDATE_SKIP_ALL_SIDEEFFECTS | Block.UPDATE_CLIENTS);
        level.updateNeighborsAt(pos, blockState.getBlock());
    }

    private static boolean doesStructureContain(BlockPos structureBlockPos, BlockPos posToTest, ServerLevel level) {
        return level.getBlockEntity(structureBlockPos) instanceof TestInstanceBlockEntity testInstanceBlockEntity
            && testInstanceBlockEntity.getStructureBoundingBox().isInside(posToTest);
    }
}
