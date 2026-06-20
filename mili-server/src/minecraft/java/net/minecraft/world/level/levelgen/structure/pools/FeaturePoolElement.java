package net.minecraft.world.level.levelgen.structure.pools;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class FeaturePoolElement extends StructurePoolElement {
    public static final MapCodec<FeaturePoolElement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(PlacedFeature.CODEC.fieldOf("feature").forGetter(featurePoolElement -> featurePoolElement.feature), projectionCodec())
            .apply(instance, FeaturePoolElement::new)
    );
    private static final Identifier DEFAULT_JIGSAW_NAME = Identifier.withDefaultNamespace("bottom");
    private final Holder<PlacedFeature> feature;
    private final CompoundTag defaultJigsawNBT;

    protected FeaturePoolElement(Holder<PlacedFeature> feature, StructureTemplatePool.Projection projection) {
        super(projection);
        this.feature = feature;
        this.defaultJigsawNBT = this.fillDefaultJigsawNBT();
    }

    private CompoundTag fillDefaultJigsawNBT() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.store("name", Identifier.CODEC, DEFAULT_JIGSAW_NAME);
        compoundTag.putString("final_state", "minecraft:air");
        compoundTag.store("pool", JigsawBlockEntity.POOL_CODEC, Pools.EMPTY);
        compoundTag.store("target", Identifier.CODEC, JigsawBlockEntity.EMPTY_ID);
        compoundTag.store("joint", JigsawBlockEntity.JointType.CODEC, JigsawBlockEntity.JointType.ROLLABLE);
        return compoundTag;
    }

    @Override
    public Vec3i getSize(StructureTemplateManager structureTemplateManager, Rotation rotation) {
        return Vec3i.ZERO;
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(
        StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation, RandomSource random
    ) {
        return List.of(
            StructureTemplate.JigsawBlockInfo.of(
                new StructureTemplate.StructureBlockInfo(
                    pos,
                    Blocks.JIGSAW.defaultBlockState().setValue(JigsawBlock.ORIENTATION, FrontAndTop.fromFrontAndTop(Direction.DOWN, Direction.SOUTH)),
                    this.defaultJigsawNBT
                )
            )
        );
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation) {
        Vec3i size = this.getSize(structureTemplateManager, rotation);
        return new BoundingBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + size.getX(), pos.getY() + size.getY(), pos.getZ() + size.getZ());
    }

    @Override
    public boolean place(
        StructureTemplateManager structureTemplateManager,
        WorldGenLevel level,
        StructureManager structureManager,
        ChunkGenerator generator,
        BlockPos offset,
        BlockPos pos,
        Rotation rotation,
        BoundingBox box,
        RandomSource random,
        LiquidSettings liquidSettings,
        boolean keepJigsaws
    ) {
        return this.feature.value().place(level, generator, random, offset);
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return StructurePoolElementType.FEATURE;
    }

    @Override
    public String toString() {
        return "Feature[" + this.feature + "]";
    }
}
