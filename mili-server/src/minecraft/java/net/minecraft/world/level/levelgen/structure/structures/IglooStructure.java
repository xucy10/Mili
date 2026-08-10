package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class IglooStructure extends Structure {
    public static final MapCodec<IglooStructure> CODEC = simpleCodec(IglooStructure::new);

    public IglooStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, structurePiecesBuilder -> this.generatePieces(structurePiecesBuilder, context));
    }

    private void generatePieces(StructurePiecesBuilder builder, Structure.GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        WorldgenRandom worldgenRandom = context.random();
        BlockPos blockPos = new BlockPos(chunkPos.getMinBlockX(), 90, chunkPos.getMinBlockZ());
        Rotation random = Rotation.getRandom(worldgenRandom);
        IglooPieces.addPieces(context.structureTemplateManager(), blockPos, random, builder, worldgenRandom);
    }

    @Override
    public StructureType<?> type() {
        return StructureType.IGLOO;
    }
}
