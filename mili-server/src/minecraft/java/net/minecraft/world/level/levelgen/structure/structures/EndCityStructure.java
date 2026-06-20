package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class EndCityStructure extends Structure {
    public static final MapCodec<EndCityStructure> CODEC = simpleCodec(EndCityStructure::new);

    public EndCityStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        Rotation random = Rotation.getRandom(context.random());
        BlockPos lowestYIn5by5BoxOffset7Blocks = this.getLowestYIn5by5BoxOffset7Blocks(context, random);
        return lowestYIn5by5BoxOffset7Blocks.getY() < 60
            ? Optional.empty()
            : Optional.of(
                new Structure.GenerationStub(
                    lowestYIn5by5BoxOffset7Blocks,
                    structurePiecesBuilder -> this.generatePieces(structurePiecesBuilder, lowestYIn5by5BoxOffset7Blocks, random, context)
                )
            );
    }

    private void generatePieces(StructurePiecesBuilder builder, BlockPos startPos, Rotation rotation, Structure.GenerationContext context) {
        List<StructurePiece> list = Lists.newArrayList();
        EndCityPieces.startHouseTower(context.structureTemplateManager(), startPos, rotation, list, context.random());
        list.forEach(builder::addPiece);
    }

    @Override
    public StructureType<?> type() {
        return StructureType.END_CITY;
    }
}
