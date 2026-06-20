package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class MineshaftStructure extends Structure {
    public static final MapCodec<MineshaftStructure> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(settingsCodec(instance), MineshaftStructure.Type.CODEC.fieldOf("mineshaft_type").forGetter(structure -> structure.type))
            .apply(instance, MineshaftStructure::new)
    );
    private final MineshaftStructure.Type type;

    public MineshaftStructure(Structure.StructureSettings settings, MineshaftStructure.Type type) {
        super(settings);
        this.type = type;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        context.random().nextDouble();
        ChunkPos chunkPos = context.chunkPos();
        BlockPos blockPos = new BlockPos(chunkPos.getMiddleBlockX(), 50, chunkPos.getMinBlockZ());
        StructurePiecesBuilder structurePiecesBuilder = new StructurePiecesBuilder();
        int i = this.generatePiecesAndAdjust(structurePiecesBuilder, context);
        return Optional.of(new Structure.GenerationStub(blockPos.offset(0, i, 0), Either.right(structurePiecesBuilder)));
    }

    private int generatePiecesAndAdjust(StructurePiecesBuilder builder, Structure.GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        WorldgenRandom worldgenRandom = context.random();
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        MineshaftPieces.MineShaftRoom mineShaftRoom = new MineshaftPieces.MineShaftRoom(
            0, worldgenRandom, chunkPos.getBlockX(2), chunkPos.getBlockZ(2), this.type
        );
        builder.addPiece(mineShaftRoom);
        mineShaftRoom.addChildren(mineShaftRoom, builder, worldgenRandom);
        int seaLevel = chunkGenerator.getSeaLevel();
        if (this.type == MineshaftStructure.Type.MESA) {
            BlockPos center = builder.getBoundingBox().getCenter();
            int baseHeight = chunkGenerator.getBaseHeight(
                center.getX(), center.getZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()
            );
            int i = baseHeight <= seaLevel ? seaLevel : Mth.randomBetweenInclusive(worldgenRandom, seaLevel, baseHeight);
            int i1 = i - center.getY();
            builder.offsetPiecesVertically(i1);
            return i1;
        } else {
            return builder.moveBelowSeaLevel(seaLevel, chunkGenerator.getMinY(), worldgenRandom, 10);
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.MINESHAFT;
    }

    public static enum Type implements StringRepresentable {
        NORMAL("normal", Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_FENCE),
        MESA("mesa", Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE);

        public static final Codec<MineshaftStructure.Type> CODEC = StringRepresentable.fromEnum(MineshaftStructure.Type::values);
        private static final IntFunction<MineshaftStructure.Type> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        private final String name;
        private final BlockState woodState;
        private final BlockState planksState;
        private final BlockState fenceState;

        private Type(final String name, final Block woodBlock, final Block planksBlock, final Block fenceBlock) {
            this.name = name;
            this.woodState = woodBlock.defaultBlockState();
            this.planksState = planksBlock.defaultBlockState();
            this.fenceState = fenceBlock.defaultBlockState();
        }

        public String getName() {
            return this.name;
        }

        public static MineshaftStructure.Type byId(int id) {
            return BY_ID.apply(id);
        }

        public BlockState getWoodState() {
            return this.woodState;
        }

        public BlockState getPlanksState() {
            return this.planksState;
        }

        public BlockState getFenceState() {
            return this.fenceState;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
