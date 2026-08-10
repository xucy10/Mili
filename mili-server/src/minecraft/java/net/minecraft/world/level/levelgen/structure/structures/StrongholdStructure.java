package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class StrongholdStructure extends Structure {
    public static final MapCodec<StrongholdStructure> CODEC = simpleCodec(StrongholdStructure::new);

    public StrongholdStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        return Optional.of(
            new Structure.GenerationStub(context.chunkPos().getWorldPosition(), structurePiecesBuilder -> generatePieces(structurePiecesBuilder, context))
        );
    }

    private static void generatePieces(StructurePiecesBuilder builder, Structure.GenerationContext context) {
        int i = 0;

        StrongholdPieces.StartPiece startPiece;
        do {
            builder.clear();
            context.random().setLargeFeatureSeed(context.seed() + i++, context.chunkPos().x, context.chunkPos().z);
            StrongholdPieces.resetPieces();
            startPiece = new StrongholdPieces.StartPiece(context.random(), context.chunkPos().getBlockX(2), context.chunkPos().getBlockZ(2));
            builder.addPiece(startPiece);
            startPiece.addChildren(startPiece, builder, context.random());
            List<StructurePiece> list = startPiece.pendingChildren;

            while (!list.isEmpty()) {
                int randomInt = context.random().nextInt(list.size());
                StructurePiece structurePiece = list.remove(randomInt);
                structurePiece.addChildren(startPiece, builder, context.random());
            }

            builder.moveBelowSeaLevel(context.chunkGenerator().getSeaLevel(), context.chunkGenerator().getMinY(), context.random(), 10);
        } while (builder.isEmpty() || startPiece.portalRoomPiece == null);
    }

    @Override
    public StructureType<?> type() {
        return StructureType.STRONGHOLD;
    }
}
