package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class OceanMonumentStructure extends Structure {
    public static final MapCodec<OceanMonumentStructure> CODEC = simpleCodec(OceanMonumentStructure::new);

    public OceanMonumentStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        int blockX = context.chunkPos().getBlockX(9);
        int blockZ = context.chunkPos().getBlockZ(9);

        for (Holder<Biome> holder : context.biomeSource()
            .getBiomesWithin(blockX, context.chunkGenerator().getSeaLevel(), blockZ, 29, context.randomState().sampler())) {
            if (!holder.is(BiomeTags.REQUIRED_OCEAN_MONUMENT_SURROUNDING)) {
                return Optional.empty();
            }
        }

        return onTopOfChunkCenter(context, Heightmap.Types.OCEAN_FLOOR_WG, structurePiecesBuilder -> generatePieces(structurePiecesBuilder, context));
    }

    private static StructurePiece createTopPiece(ChunkPos chunkPos, WorldgenRandom random) {
        int i = chunkPos.getMinBlockX() - 29;
        int i1 = chunkPos.getMinBlockZ() - 29;
        Direction randomDirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        return new OceanMonumentPieces.MonumentBuilding(random, i, i1, randomDirection);
    }

    private static void generatePieces(StructurePiecesBuilder builder, Structure.GenerationContext context) {
        builder.addPiece(createTopPiece(context.chunkPos(), context.random()));
    }

    public static PiecesContainer regeneratePiecesAfterLoad(ChunkPos chunkPos, long seed, PiecesContainer piecesContainer) {
        if (piecesContainer.isEmpty()) {
            return piecesContainer;
        } else {
            WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            worldgenRandom.setLargeFeatureSeed(seed, chunkPos.x, chunkPos.z);
            StructurePiece structurePiece = piecesContainer.pieces().get(0);
            BoundingBox boundingBox = structurePiece.getBoundingBox();
            int minX = boundingBox.minX();
            int minZ = boundingBox.minZ();
            Direction randomDirection = Direction.Plane.HORIZONTAL.getRandomDirection(worldgenRandom);
            Direction direction = Objects.requireNonNullElse(structurePiece.getOrientation(), randomDirection);
            StructurePiece structurePiece1 = new OceanMonumentPieces.MonumentBuilding(worldgenRandom, minX, minZ, direction);
            StructurePiecesBuilder structurePiecesBuilder = new StructurePiecesBuilder();
            structurePiecesBuilder.addPiece(structurePiece1);
            return structurePiecesBuilder.build();
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.OCEAN_MONUMENT;
    }
}
