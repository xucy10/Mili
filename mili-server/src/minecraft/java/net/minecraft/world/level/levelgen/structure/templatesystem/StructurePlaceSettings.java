package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

public class StructurePlaceSettings {
    private Mirror mirror = Mirror.NONE;
    private Rotation rotation = Rotation.NONE;
    private BlockPos rotationPivot = BlockPos.ZERO;
    private boolean ignoreEntities;
    private @Nullable BoundingBox boundingBox;
    private LiquidSettings liquidSettings = LiquidSettings.APPLY_WATERLOGGING;
    private @Nullable RandomSource random;
    public int palette = -1; // CraftBukkit - Set initial value so we know if the palette has been set forcefully
    private final List<StructureProcessor> processors = Lists.newArrayList();
    private boolean knownShape;
    private boolean finalizeEntities;

    public StructurePlaceSettings copy() {
        StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings();
        structurePlaceSettings.mirror = this.mirror;
        structurePlaceSettings.rotation = this.rotation;
        structurePlaceSettings.rotationPivot = this.rotationPivot;
        structurePlaceSettings.ignoreEntities = this.ignoreEntities;
        structurePlaceSettings.boundingBox = this.boundingBox;
        structurePlaceSettings.liquidSettings = this.liquidSettings;
        structurePlaceSettings.random = this.random;
        structurePlaceSettings.palette = this.palette;
        structurePlaceSettings.processors.addAll(this.processors);
        structurePlaceSettings.knownShape = this.knownShape;
        structurePlaceSettings.finalizeEntities = this.finalizeEntities;
        return structurePlaceSettings;
    }

    public StructurePlaceSettings setMirror(Mirror mirror) {
        this.mirror = mirror;
        return this;
    }

    public StructurePlaceSettings setRotation(Rotation rotation) {
        this.rotation = rotation;
        return this;
    }

    public StructurePlaceSettings setRotationPivot(BlockPos rotationPivot) {
        this.rotationPivot = rotationPivot;
        return this;
    }

    public StructurePlaceSettings setIgnoreEntities(boolean ignoreEntities) {
        this.ignoreEntities = ignoreEntities;
        return this;
    }

    public StructurePlaceSettings setBoundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
        return this;
    }

    public StructurePlaceSettings setRandom(@Nullable RandomSource random) {
        this.random = random;
        return this;
    }

    public StructurePlaceSettings setLiquidSettings(LiquidSettings liquidSettings) {
        this.liquidSettings = liquidSettings;
        return this;
    }

    public StructurePlaceSettings setKnownShape(boolean knownShape) {
        this.knownShape = knownShape;
        return this;
    }

    public StructurePlaceSettings clearProcessors() {
        this.processors.clear();
        return this;
    }

    public StructurePlaceSettings addProcessor(StructureProcessor processor) {
        this.processors.add(processor);
        return this;
    }

    public StructurePlaceSettings popProcessor(StructureProcessor processor) {
        this.processors.remove(processor);
        return this;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public BlockPos getRotationPivot() {
        return this.rotationPivot;
    }

    public RandomSource getRandom(@Nullable BlockPos seedPos) {
        if (this.random != null) {
            return this.random;
        } else {
            return seedPos == null ? RandomSource.create(Util.getMillis()) : RandomSource.create(Mth.getSeed(seedPos));
        }
    }

    public boolean isIgnoreEntities() {
        return this.ignoreEntities;
    }

    public @Nullable BoundingBox getBoundingBox() {
        return this.boundingBox;
    }

    public boolean getKnownShape() {
        return this.knownShape;
    }

    public List<StructureProcessor> getProcessors() {
        return this.processors;
    }

    public boolean shouldApplyWaterlogging() {
        return this.liquidSettings == LiquidSettings.APPLY_WATERLOGGING;
    }

    public StructureTemplate.Palette getRandomPalette(List<StructureTemplate.Palette> palettes, @Nullable BlockPos pos) {
        int size = palettes.size();
        if (size == 0) {
            throw new IllegalStateException("No palettes");
        // CraftBukkit start
        } else if (this.palette >= 0) {
            if (this.palette >= size) {
                throw new IllegalArgumentException("Palette index out of bounds. Got " + this.palette + " where there are only " + size + " palettes available.");
            }
            return palettes.get(this.palette);
        // CraftBukkit end
        } else {
            return palettes.get(this.getRandom(pos).nextInt(size));
        }
    }

    public StructurePlaceSettings setFinalizeEntities(boolean finalizeEntities) {
        this.finalizeEntities = finalizeEntities;
        return this;
    }

    public boolean shouldFinalizeEntities() {
        return this.finalizeEntities;
    }
}
