package net.minecraft.data;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

public class BlockFamily {
    private final Block baseBlock;
    final Map<BlockFamily.Variant, Block> variants = Maps.newHashMap();
    boolean generateModel = true;
    boolean generateRecipe = true;
    @Nullable String recipeGroupPrefix;
    @Nullable String recipeUnlockedBy;

    BlockFamily(Block baseBlock) {
        this.baseBlock = baseBlock;
    }

    public Block getBaseBlock() {
        return this.baseBlock;
    }

    public Map<BlockFamily.Variant, Block> getVariants() {
        return this.variants;
    }

    public Block get(BlockFamily.Variant variant) {
        return this.variants.get(variant);
    }

    public boolean shouldGenerateModel() {
        return this.generateModel;
    }

    public boolean shouldGenerateRecipe() {
        return this.generateRecipe;
    }

    public Optional<String> getRecipeGroupPrefix() {
        return StringUtil.isBlank(this.recipeGroupPrefix) ? Optional.empty() : Optional.of(this.recipeGroupPrefix);
    }

    public Optional<String> getRecipeUnlockedBy() {
        return StringUtil.isBlank(this.recipeUnlockedBy) ? Optional.empty() : Optional.of(this.recipeUnlockedBy);
    }

    public static class Builder {
        private final BlockFamily family;

        public Builder(Block baseBlock) {
            this.family = new BlockFamily(baseBlock);
        }

        public BlockFamily getFamily() {
            return this.family;
        }

        public BlockFamily.Builder button(Block buttonBlock) {
            this.family.variants.put(BlockFamily.Variant.BUTTON, buttonBlock);
            return this;
        }

        public BlockFamily.Builder chiseled(Block chiseledBlock) {
            this.family.variants.put(BlockFamily.Variant.CHISELED, chiseledBlock);
            return this;
        }

        public BlockFamily.Builder mosaic(Block mosaicBlock) {
            this.family.variants.put(BlockFamily.Variant.MOSAIC, mosaicBlock);
            return this;
        }

        public BlockFamily.Builder cracked(Block crackedBlock) {
            this.family.variants.put(BlockFamily.Variant.CRACKED, crackedBlock);
            return this;
        }

        public BlockFamily.Builder cut(Block cutBlock) {
            this.family.variants.put(BlockFamily.Variant.CUT, cutBlock);
            return this;
        }

        public BlockFamily.Builder door(Block doorBlock) {
            this.family.variants.put(BlockFamily.Variant.DOOR, doorBlock);
            return this;
        }

        public BlockFamily.Builder customFence(Block customFenceBlock) {
            this.family.variants.put(BlockFamily.Variant.CUSTOM_FENCE, customFenceBlock);
            return this;
        }

        public BlockFamily.Builder fence(Block fenceBlock) {
            this.family.variants.put(BlockFamily.Variant.FENCE, fenceBlock);
            return this;
        }

        public BlockFamily.Builder customFenceGate(Block customFenceGateBlock) {
            this.family.variants.put(BlockFamily.Variant.CUSTOM_FENCE_GATE, customFenceGateBlock);
            return this;
        }

        public BlockFamily.Builder fenceGate(Block fenceGateBlock) {
            this.family.variants.put(BlockFamily.Variant.FENCE_GATE, fenceGateBlock);
            return this;
        }

        public BlockFamily.Builder sign(Block signBlock, Block wallSignBlock) {
            this.family.variants.put(BlockFamily.Variant.SIGN, signBlock);
            this.family.variants.put(BlockFamily.Variant.WALL_SIGN, wallSignBlock);
            return this;
        }

        public BlockFamily.Builder slab(Block slabBlock) {
            this.family.variants.put(BlockFamily.Variant.SLAB, slabBlock);
            return this;
        }

        public BlockFamily.Builder stairs(Block stairsBlock) {
            this.family.variants.put(BlockFamily.Variant.STAIRS, stairsBlock);
            return this;
        }

        public BlockFamily.Builder pressurePlate(Block pressurePlateBlock) {
            this.family.variants.put(BlockFamily.Variant.PRESSURE_PLATE, pressurePlateBlock);
            return this;
        }

        public BlockFamily.Builder polished(Block polishedBlock) {
            this.family.variants.put(BlockFamily.Variant.POLISHED, polishedBlock);
            return this;
        }

        public BlockFamily.Builder trapdoor(Block trapdoorBlock) {
            this.family.variants.put(BlockFamily.Variant.TRAPDOOR, trapdoorBlock);
            return this;
        }

        public BlockFamily.Builder wall(Block wallBlock) {
            this.family.variants.put(BlockFamily.Variant.WALL, wallBlock);
            return this;
        }

        public BlockFamily.Builder dontGenerateModel() {
            this.family.generateModel = false;
            return this;
        }

        public BlockFamily.Builder dontGenerateRecipe() {
            this.family.generateRecipe = false;
            return this;
        }

        public BlockFamily.Builder recipeGroupPrefix(String recipeGroupPrefix) {
            this.family.recipeGroupPrefix = recipeGroupPrefix;
            return this;
        }

        public BlockFamily.Builder recipeUnlockedBy(String recipeUnlockedBy) {
            this.family.recipeUnlockedBy = recipeUnlockedBy;
            return this;
        }
    }

    public static enum Variant {
        BUTTON("button"),
        CHISELED("chiseled"),
        CRACKED("cracked"),
        CUT("cut"),
        DOOR("door"),
        CUSTOM_FENCE("fence"),
        FENCE("fence"),
        CUSTOM_FENCE_GATE("fence_gate"),
        FENCE_GATE("fence_gate"),
        MOSAIC("mosaic"),
        SIGN("sign"),
        SLAB("slab"),
        STAIRS("stairs"),
        PRESSURE_PLATE("pressure_plate"),
        POLISHED("polished"),
        TRAPDOOR("trapdoor"),
        WALL("wall"),
        WALL_SIGN("wall_sign");

        private final String recipeGroup;

        private Variant(final String recipeGroup) {
            this.recipeGroup = recipeGroup;
        }

        public String getRecipeGroup() {
            return this.recipeGroup;
        }
    }
}
