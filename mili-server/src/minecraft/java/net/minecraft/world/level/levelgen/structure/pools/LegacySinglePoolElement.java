package net.minecraft.world.level.levelgen.structure.pools;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class LegacySinglePoolElement extends SinglePoolElement {
    public static final MapCodec<LegacySinglePoolElement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(templateCodec(), processorsCodec(), projectionCodec(), overrideLiquidSettingsCodec())
            .apply(instance, LegacySinglePoolElement::new)
    );

    protected LegacySinglePoolElement(
        Either<Identifier, StructureTemplate> template,
        Holder<StructureProcessorList> processors,
        StructureTemplatePool.Projection projection,
        Optional<LiquidSettings> overrideLiquidSettings
    ) {
        super(template, processors, projection, overrideLiquidSettings);
    }

    @Override
    protected StructurePlaceSettings getSettings(Rotation rotation, BoundingBox boundingBox, LiquidSettings liquidSettings, boolean offset) {
        StructurePlaceSettings structurePlaceSettings = super.getSettings(rotation, boundingBox, liquidSettings, offset);
        structurePlaceSettings.popProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        structurePlaceSettings.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        return structurePlaceSettings;
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return StructurePoolElementType.LEGACY;
    }

    @Override
    public String toString() {
        return "LegacySingle[" + this.template + "]";
    }
}
