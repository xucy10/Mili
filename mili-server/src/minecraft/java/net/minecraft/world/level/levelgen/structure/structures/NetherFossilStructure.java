package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public class NetherFossilStructure extends Structure {
    public static final MapCodec<NetherFossilStructure> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(settingsCodec(instance), HeightProvider.CODEC.fieldOf("height").forGetter(structure -> structure.height))
            .apply(instance, NetherFossilStructure::new)
    );
    public final HeightProvider height;

    public NetherFossilStructure(Structure.StructureSettings settings, HeightProvider height) {
        super(settings);
        this.height = height;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        WorldgenRandom worldgenRandom = context.random();
        int i = context.chunkPos().getMinBlockX() + worldgenRandom.nextInt(16);
        int i1 = context.chunkPos().getMinBlockZ() + worldgenRandom.nextInt(16);
        int seaLevel = context.chunkGenerator().getSeaLevel();
        WorldGenerationContext worldGenerationContext = new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor());
        int i2 = this.height.sample(worldgenRandom, worldGenerationContext);
        NoiseColumn baseColumn = context.chunkGenerator().getBaseColumn(i, i1, context.heightAccessor(), context.randomState());
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(i, i2, i1);

        while (i2 > seaLevel) {
            BlockState block = baseColumn.getBlock(i2);
            BlockState block1 = baseColumn.getBlock(--i2);
            if (block.isAir() && (block1.is(Blocks.SOUL_SAND) || block1.isFaceSturdy(EmptyBlockGetter.INSTANCE, mutableBlockPos.setY(i2), Direction.UP))) {
                break;
            }
        }

        if (i2 <= seaLevel) {
            return Optional.empty();
        } else {
            BlockPos blockPos = new BlockPos(i, i2, i1);
            return Optional.of(
                new Structure.GenerationStub(
                    blockPos,
                    structurePiecesBuilder -> NetherFossilPieces.addPieces(context.structureTemplateManager(), structurePiecesBuilder, worldgenRandom, blockPos)
                )
            );
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.NETHER_FOSSIL;
    }
}
