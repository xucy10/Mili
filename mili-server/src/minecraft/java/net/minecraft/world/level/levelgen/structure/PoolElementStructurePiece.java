package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.Lists;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class PoolElementStructurePiece extends StructurePiece {
    protected final StructurePoolElement element;
    protected BlockPos position;
    private final int groundLevelDelta;
    protected final Rotation rotation;
    private final List<JigsawJunction> junctions = Lists.newArrayList();
    private final StructureTemplateManager structureTemplateManager;
    private final LiquidSettings liquidSettings;

    public PoolElementStructurePiece(
        StructureTemplateManager structureTemplateManager,
        StructurePoolElement element,
        BlockPos position,
        int groundLevelDelta,
        Rotation rotation,
        BoundingBox boundingBox,
        LiquidSettings liquidSettings
    ) {
        super(StructurePieceType.JIGSAW, 0, boundingBox);
        this.structureTemplateManager = structureTemplateManager;
        this.element = element;
        this.position = position;
        this.groundLevelDelta = groundLevelDelta;
        this.rotation = rotation;
        this.liquidSettings = liquidSettings;
    }

    public PoolElementStructurePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(StructurePieceType.JIGSAW, tag);
        this.structureTemplateManager = context.structureTemplateManager();
        this.position = new BlockPos(tag.getIntOr("PosX", 0), tag.getIntOr("PosY", 0), tag.getIntOr("PosZ", 0));
        this.groundLevelDelta = tag.getIntOr("ground_level_delta", 0);
        DynamicOps<Tag> dynamicOps = context.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        this.element = tag.read("pool_element", StructurePoolElement.CODEC, dynamicOps)
            .orElseThrow(() -> new IllegalStateException("Invalid pool element found"));
        this.rotation = tag.read("rotation", Rotation.LEGACY_CODEC).orElseThrow();
        this.boundingBox = this.element.getBoundingBox(this.structureTemplateManager, this.position, this.rotation);
        ListTag listOrEmpty = tag.getListOrEmpty("junctions");
        this.junctions.clear();
        listOrEmpty.forEach(junctionTag -> this.junctions.add(JigsawJunction.deserialize(new Dynamic<>(dynamicOps, junctionTag))));
        this.liquidSettings = tag.read("liquid_settings", LiquidSettings.CODEC).orElse(JigsawStructure.DEFAULT_LIQUID_SETTINGS);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("PosX", this.position.getX());
        tag.putInt("PosY", this.position.getY());
        tag.putInt("PosZ", this.position.getZ());
        tag.putInt("ground_level_delta", this.groundLevelDelta);
        DynamicOps<Tag> dynamicOps = context.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        tag.store("pool_element", StructurePoolElement.CODEC, dynamicOps, this.element);
        tag.store("rotation", Rotation.LEGACY_CODEC, this.rotation);
        ListTag listTag = new ListTag();

        for (JigsawJunction jigsawJunction : this.junctions) {
            listTag.add(jigsawJunction.serialize(dynamicOps).getValue());
        }

        tag.put("junctions", listTag);
        if (this.liquidSettings != JigsawStructure.DEFAULT_LIQUID_SETTINGS) {
            tag.store("liquid_settings", LiquidSettings.CODEC, dynamicOps, this.liquidSettings);
        }
    }

    @Override
    public void postProcess(
        WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos
    ) {
        this.place(level, structureManager, generator, random, box, pos, false);
    }

    public void place(
        WorldGenLevel level,
        StructureManager structureManager,
        ChunkGenerator generator,
        RandomSource random,
        BoundingBox box,
        BlockPos pos,
        boolean keepJigsaws
    ) {
        this.element
            .place(
                this.structureTemplateManager,
                level,
                structureManager,
                generator,
                this.position,
                pos,
                this.rotation,
                box,
                random,
                this.liquidSettings,
                keepJigsaws
            );
    }

    @Override
    public void move(int x, int y, int z) {
        super.move(x, y, z);
        this.position = this.position.offset(x, y, z);
    }

    @Override
    public Rotation getRotation() {
        return this.rotation;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "<%s | %s | %s | %s>", this.getClass().getSimpleName(), this.position, this.rotation, this.element);
    }

    public StructurePoolElement getElement() {
        return this.element;
    }

    public BlockPos getPosition() {
        return this.position;
    }

    public int getGroundLevelDelta() {
        return this.groundLevelDelta;
    }

    public void addJunction(JigsawJunction junction) {
        this.junctions.add(junction);
    }

    public List<JigsawJunction> getJunctions() {
        return this.junctions;
    }
}
