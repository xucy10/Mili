package net.minecraft.world.level.levelgen.structure.pools;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class SinglePoolElement extends StructurePoolElement {
    private static final Comparator<StructureTemplate.JigsawBlockInfo> HIGHEST_SELECTION_PRIORITY_FIRST = Comparator.comparingInt(
            StructureTemplate.JigsawBlockInfo::selectionPriority
        )
        .reversed();
    private static final Codec<Either<Identifier, StructureTemplate>> TEMPLATE_CODEC = Codec.of(
        SinglePoolElement::encodeTemplate, Identifier.CODEC.map(Either::left)
    );
    public static final MapCodec<SinglePoolElement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(templateCodec(), processorsCodec(), projectionCodec(), overrideLiquidSettingsCodec())
            .apply(instance, SinglePoolElement::new)
    );
    protected final Either<Identifier, StructureTemplate> template;
    protected final Holder<StructureProcessorList> processors;
    protected final Optional<LiquidSettings> overrideLiquidSettings;

    private static <T> DataResult<T> encodeTemplate(Either<Identifier, StructureTemplate> template, DynamicOps<T> ops, T values) {
        Optional<Identifier> optional = template.left();
        return optional.isEmpty() ? DataResult.error(() -> "Can not serialize a runtime pool element") : Identifier.CODEC.encode(optional.get(), ops, values);
    }

    protected static <E extends SinglePoolElement> RecordCodecBuilder<E, Holder<StructureProcessorList>> processorsCodec() {
        return StructureProcessorType.LIST_CODEC.fieldOf("processors").forGetter(singlePoolElement -> singlePoolElement.processors);
    }

    protected static <E extends SinglePoolElement> RecordCodecBuilder<E, Optional<LiquidSettings>> overrideLiquidSettingsCodec() {
        return LiquidSettings.CODEC.optionalFieldOf("override_liquid_settings").forGetter(singlePoolElement -> singlePoolElement.overrideLiquidSettings);
    }

    protected static <E extends SinglePoolElement> RecordCodecBuilder<E, Either<Identifier, StructureTemplate>> templateCodec() {
        return TEMPLATE_CODEC.fieldOf("location").forGetter(singlePoolElement -> singlePoolElement.template);
    }

    protected SinglePoolElement(
        Either<Identifier, StructureTemplate> template,
        Holder<StructureProcessorList> processors,
        StructureTemplatePool.Projection projection,
        Optional<LiquidSettings> overrideLiquidSettings
    ) {
        super(projection);
        this.template = template;
        this.processors = processors;
        this.overrideLiquidSettings = overrideLiquidSettings;
    }

    @Override
    public Vec3i getSize(StructureTemplateManager structureTemplateManager, Rotation rotation) {
        StructureTemplate template = this.getTemplate(structureTemplateManager);
        return template.getSize(rotation);
    }

    private StructureTemplate getTemplate(StructureTemplateManager structureTemplateManager) {
        return this.template.map(structureTemplateManager::getOrCreate, Function.identity());
    }

    public List<StructureTemplate.StructureBlockInfo> getDataMarkers(
        StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation, boolean relativePosition
    ) {
        StructureTemplate template = this.getTemplate(structureTemplateManager);
        List<StructureTemplate.StructureBlockInfo> list = template.filterBlocks(
            pos, new StructurePlaceSettings().setRotation(rotation), Blocks.STRUCTURE_BLOCK, relativePosition
        );
        List<StructureTemplate.StructureBlockInfo> list1 = Lists.newArrayList();

        for (StructureTemplate.StructureBlockInfo structureBlockInfo : list) {
            CompoundTag compoundTag = structureBlockInfo.nbt();
            if (compoundTag != null) {
                StructureMode structureMode = compoundTag.read("mode", StructureMode.LEGACY_CODEC).orElseThrow();
                if (structureMode == StructureMode.DATA) {
                    list1.add(structureBlockInfo);
                }
            }
        }

        return list1;
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(
        StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation, RandomSource random
    ) {
        List<StructureTemplate.JigsawBlockInfo> jigsaws = this.getTemplate(structureTemplateManager).getJigsaws(pos, rotation);
        Util.shuffle(jigsaws, random);
        sortBySelectionPriority(jigsaws);
        return jigsaws;
    }

    @VisibleForTesting
    static void sortBySelectionPriority(List<StructureTemplate.JigsawBlockInfo> structureBlockInfos) {
        structureBlockInfos.sort(HIGHEST_SELECTION_PRIORITY_FIRST);
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation) {
        StructureTemplate template = this.getTemplate(structureTemplateManager);
        return template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), pos);
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
        StructureTemplate template = this.getTemplate(structureTemplateManager);
        StructurePlaceSettings settings = this.getSettings(rotation, box, liquidSettings, keepJigsaws);
        if (!template.placeInWorld(level, offset, pos, settings, random, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
            return false;
        } else {
            for (StructureTemplate.StructureBlockInfo structureBlockInfo : StructureTemplate.processBlockInfos(
                level, offset, pos, settings, this.getDataMarkers(structureTemplateManager, offset, rotation, false)
            )) {
                this.handleDataMarker(level, structureBlockInfo, offset, rotation, random, box);
            }

            return true;
        }
    }

    protected StructurePlaceSettings getSettings(Rotation rotation, BoundingBox boundingBox, LiquidSettings liquidSettings, boolean offset) {
        StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings();
        structurePlaceSettings.setBoundingBox(boundingBox);
        structurePlaceSettings.setRotation(rotation);
        structurePlaceSettings.setKnownShape(true);
        structurePlaceSettings.setIgnoreEntities(false);
        structurePlaceSettings.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        structurePlaceSettings.setFinalizeEntities(true);
        structurePlaceSettings.setLiquidSettings(this.overrideLiquidSettings.orElse(liquidSettings));
        if (!offset) {
            structurePlaceSettings.addProcessor(JigsawReplacementProcessor.INSTANCE);
        }

        this.processors.value().list().forEach(structurePlaceSettings::addProcessor);
        this.getProjection().getProcessors().forEach(structurePlaceSettings::addProcessor);
        return structurePlaceSettings;
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return StructurePoolElementType.SINGLE;
    }

    @Override
    public String toString() {
        return "Single[" + this.template + "]";
    }

    @VisibleForTesting
    public Identifier getTemplateLocation() {
        return this.template.orThrow();
    }
}
