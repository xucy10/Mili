package net.minecraft.world.level.levelgen.structure.pools;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class ListPoolElement extends StructurePoolElement {
    public static final MapCodec<ListPoolElement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                StructurePoolElement.CODEC.listOf().fieldOf("elements").forGetter(listPoolElement -> listPoolElement.elements), projectionCodec()
            )
            .apply(instance, ListPoolElement::new)
    );
    private final List<StructurePoolElement> elements;

    public ListPoolElement(List<StructurePoolElement> elements, StructureTemplatePool.Projection projection) {
        super(projection);
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("Elements are empty");
        } else {
            this.elements = elements;
            this.setProjectionOnEachElement(projection);
        }
    }

    @Override
    public Vec3i getSize(StructureTemplateManager structureTemplateManager, Rotation rotation) {
        int i = 0;
        int i1 = 0;
        int i2 = 0;

        for (StructurePoolElement structurePoolElement : this.elements) {
            Vec3i size = structurePoolElement.getSize(structureTemplateManager, rotation);
            i = Math.max(i, size.getX());
            i1 = Math.max(i1, size.getY());
            i2 = Math.max(i2, size.getZ());
        }

        return new Vec3i(i, i1, i2);
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(
        StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation, RandomSource random
    ) {
        return this.elements.get(0).getShuffledJigsawBlocks(structureTemplateManager, pos, rotation, random);
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation) {
        Stream<BoundingBox> stream = this.elements
            .stream()
            .filter(structurePoolElement -> structurePoolElement != EmptyPoolElement.INSTANCE)
            .map(structurePoolElement -> structurePoolElement.getBoundingBox(structureTemplateManager, pos, rotation));
        return BoundingBox.encapsulatingBoxes(stream::iterator)
            .orElseThrow(() -> new IllegalStateException("Unable to calculate boundingbox for ListPoolElement"));
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
        for (StructurePoolElement structurePoolElement : this.elements) {
            if (!structurePoolElement.place(
                structureTemplateManager, level, structureManager, generator, offset, pos, rotation, box, random, liquidSettings, keepJigsaws
            )) {
                return false;
            }
        }

        return true;
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return StructurePoolElementType.LIST;
    }

    @Override
    public StructurePoolElement setProjection(StructureTemplatePool.Projection projection) {
        super.setProjection(projection);
        this.setProjectionOnEachElement(projection);
        return this;
    }

    @Override
    public String toString() {
        return "List[" + this.elements.stream().map(Object::toString).collect(Collectors.joining(", ")) + "]";
    }

    private void setProjectionOnEachElement(StructureTemplatePool.Projection projection) {
        this.elements.forEach(structurePoolElement -> structurePoolElement.setProjection(projection));
    }

    @VisibleForTesting
    public List<StructurePoolElement> getElements() {
        return this.elements;
    }
}
