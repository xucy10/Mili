package net.minecraft.world.entity.animal.fish;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TropicalFish extends AbstractSchoolingFish {
    public static final TropicalFish.Variant DEFAULT_VARIANT = new TropicalFish.Variant(TropicalFish.Pattern.KOB, DyeColor.WHITE, DyeColor.WHITE);
    private static final EntityDataAccessor<Integer> DATA_ID_TYPE_VARIANT = SynchedEntityData.defineId(TropicalFish.class, EntityDataSerializers.INT);
    public static final List<TropicalFish.Variant> COMMON_VARIANTS = List.of(
        new TropicalFish.Variant(TropicalFish.Pattern.STRIPEY, DyeColor.ORANGE, DyeColor.GRAY),
        new TropicalFish.Variant(TropicalFish.Pattern.FLOPPER, DyeColor.GRAY, DyeColor.GRAY),
        new TropicalFish.Variant(TropicalFish.Pattern.FLOPPER, DyeColor.GRAY, DyeColor.BLUE),
        new TropicalFish.Variant(TropicalFish.Pattern.CLAYFISH, DyeColor.WHITE, DyeColor.GRAY),
        new TropicalFish.Variant(TropicalFish.Pattern.SUNSTREAK, DyeColor.BLUE, DyeColor.GRAY),
        new TropicalFish.Variant(TropicalFish.Pattern.KOB, DyeColor.ORANGE, DyeColor.WHITE),
        new TropicalFish.Variant(TropicalFish.Pattern.SPOTTY, DyeColor.PINK, DyeColor.LIGHT_BLUE),
        new TropicalFish.Variant(TropicalFish.Pattern.BLOCKFISH, DyeColor.PURPLE, DyeColor.YELLOW),
        new TropicalFish.Variant(TropicalFish.Pattern.CLAYFISH, DyeColor.WHITE, DyeColor.RED),
        new TropicalFish.Variant(TropicalFish.Pattern.SPOTTY, DyeColor.WHITE, DyeColor.YELLOW),
        new TropicalFish.Variant(TropicalFish.Pattern.GLITTER, DyeColor.WHITE, DyeColor.GRAY),
        new TropicalFish.Variant(TropicalFish.Pattern.CLAYFISH, DyeColor.WHITE, DyeColor.ORANGE),
        new TropicalFish.Variant(TropicalFish.Pattern.DASHER, DyeColor.CYAN, DyeColor.PINK),
        new TropicalFish.Variant(TropicalFish.Pattern.BRINELY, DyeColor.LIME, DyeColor.LIGHT_BLUE),
        new TropicalFish.Variant(TropicalFish.Pattern.BETTY, DyeColor.RED, DyeColor.WHITE),
        new TropicalFish.Variant(TropicalFish.Pattern.SNOOPER, DyeColor.GRAY, DyeColor.RED),
        new TropicalFish.Variant(TropicalFish.Pattern.BLOCKFISH, DyeColor.RED, DyeColor.WHITE),
        new TropicalFish.Variant(TropicalFish.Pattern.FLOPPER, DyeColor.WHITE, DyeColor.YELLOW),
        new TropicalFish.Variant(TropicalFish.Pattern.KOB, DyeColor.RED, DyeColor.WHITE),
        new TropicalFish.Variant(TropicalFish.Pattern.SUNSTREAK, DyeColor.GRAY, DyeColor.WHITE),
        new TropicalFish.Variant(TropicalFish.Pattern.DASHER, DyeColor.CYAN, DyeColor.YELLOW),
        new TropicalFish.Variant(TropicalFish.Pattern.FLOPPER, DyeColor.YELLOW, DyeColor.YELLOW)
    );
    private boolean isSchool = true;

    public TropicalFish(EntityType<? extends TropicalFish> type, Level level) {
        super(type, level);
    }

    public static String getPredefinedName(int variantId) {
        return "entity.minecraft.tropical_fish.predefined." + variantId;
    }

    static int packVariant(TropicalFish.Pattern pattern, DyeColor baseColor, DyeColor patternColor) {
        return pattern.getPackedId() & 65535 | (baseColor.getId() & 0xFF) << 16 | (patternColor.getId() & 0xFF) << 24;
    }

    public static DyeColor getBaseColor(int variantId) {
        return DyeColor.byId(variantId >> 16 & 0xFF);
    }

    public static DyeColor getPatternColor(int variantId) {
        return DyeColor.byId(variantId >> 24 & 0xFF);
    }

    public static TropicalFish.Pattern getPattern(int variantId) {
        return TropicalFish.Pattern.byId(variantId & 65535);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_TYPE_VARIANT, DEFAULT_VARIANT.getPackedId());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Variant", TropicalFish.Variant.CODEC, new TropicalFish.Variant(this.getPackedVariant()));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        TropicalFish.Variant variant = input.read("Variant", TropicalFish.Variant.CODEC).orElse(DEFAULT_VARIANT);
        this.setPackedVariant(variant.getPackedId());
    }

    public void setPackedVariant(int packedVariant) {
        this.entityData.set(DATA_ID_TYPE_VARIANT, packedVariant);
    }

    @Override
    public boolean isMaxGroupSizeReached(int size) {
        return !this.isSchool;
    }

    public int getPackedVariant() {
        return this.entityData.get(DATA_ID_TYPE_VARIANT);
    }

    public DyeColor getBaseColor() {
        return getBaseColor(this.getPackedVariant());
    }

    public DyeColor getPatternColor() {
        return getPatternColor(this.getPackedVariant());
    }

    public TropicalFish.Pattern getPattern() {
        return getPattern(this.getPackedVariant());
    }

    private void setPattern(TropicalFish.Pattern pattern) {
        int packedVariant = this.getPackedVariant();
        DyeColor baseColor = getBaseColor(packedVariant);
        DyeColor patternColor = getPatternColor(packedVariant);
        this.setPackedVariant(packVariant(pattern, baseColor, patternColor));
    }

    private void setBaseColor(DyeColor baseColor) {
        int packedVariant = this.getPackedVariant();
        TropicalFish.Pattern pattern = getPattern(packedVariant);
        DyeColor patternColor = getPatternColor(packedVariant);
        this.setPackedVariant(packVariant(pattern, baseColor, patternColor));
    }

    private void setPatternColor(DyeColor patternColor) {
        int packedVariant = this.getPackedVariant();
        TropicalFish.Pattern pattern = getPattern(packedVariant);
        DyeColor baseColor = getBaseColor(packedVariant);
        this.setPackedVariant(packVariant(pattern, baseColor, patternColor));
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        if (component == DataComponents.TROPICAL_FISH_PATTERN) {
            return castComponentValue((DataComponentType<T>)component, this.getPattern());
        } else if (component == DataComponents.TROPICAL_FISH_BASE_COLOR) {
            return castComponentValue((DataComponentType<T>)component, this.getBaseColor());
        } else {
            return component == DataComponents.TROPICAL_FISH_PATTERN_COLOR
                ? castComponentValue((DataComponentType<T>)component, this.getPatternColor())
                : super.get(component);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.TROPICAL_FISH_PATTERN);
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.TROPICAL_FISH_BASE_COLOR);
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.TROPICAL_FISH_PATTERN_COLOR);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.TROPICAL_FISH_PATTERN) {
            this.setPattern(castComponentValue(DataComponents.TROPICAL_FISH_PATTERN, value));
            return true;
        } else if (component == DataComponents.TROPICAL_FISH_BASE_COLOR) {
            this.setBaseColor(castComponentValue(DataComponents.TROPICAL_FISH_BASE_COLOR, value));
            return true;
        } else if (component == DataComponents.TROPICAL_FISH_PATTERN_COLOR) {
            this.setPatternColor(castComponentValue(DataComponents.TROPICAL_FISH_PATTERN_COLOR, value));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        super.saveToBucketTag(stack);
        stack.copyFrom(DataComponents.TROPICAL_FISH_PATTERN, this);
        stack.copyFrom(DataComponents.TROPICAL_FISH_BASE_COLOR, this);
        stack.copyFrom(DataComponents.TROPICAL_FISH_PATTERN_COLOR, this);
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.TROPICAL_FISH_BUCKET);
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.TROPICAL_FISH_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.TROPICAL_FISH_DEATH;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.TROPICAL_FISH_HURT;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.TROPICAL_FISH_FLOP;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        RandomSource random = level.getRandom();
        TropicalFish.Variant variant;
        if (spawnGroupData instanceof TropicalFish.TropicalFishGroupData tropicalFishGroupData) {
            variant = tropicalFishGroupData.variant;
        } else if (random.nextFloat() < 0.9) {
            variant = Util.getRandom(COMMON_VARIANTS, random);
            spawnGroupData = new TropicalFish.TropicalFishGroupData(this, variant);
        } else {
            this.isSchool = false;
            TropicalFish.Pattern[] patterns = TropicalFish.Pattern.values();
            DyeColor[] dyeColors = DyeColor.values();
            TropicalFish.Pattern pattern = Util.getRandom(patterns, random);
            DyeColor dyeColor = Util.getRandom(dyeColors, random);
            DyeColor dyeColor1 = Util.getRandom(dyeColors, random);
            variant = new TropicalFish.Variant(pattern, dyeColor, dyeColor1);
        }

        this.setPackedVariant(variant.getPackedId());
        return spawnGroupData;
    }

    public static boolean checkTropicalFishSpawnRules(
        EntityType<TropicalFish> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getFluidState(pos.below()).is(FluidTags.WATER)
            && level.getBlockState(pos.above()).is(Blocks.WATER)
            && (
                level.getBiome(pos).is(BiomeTags.ALLOWS_TROPICAL_FISH_SPAWNS_AT_ANY_HEIGHT)
                    || WaterAnimal.checkSurfaceWaterAnimalSpawnRules(entityType, level, spawnReason, pos, random)
            );
    }

    public static enum Base {
        SMALL(0),
        LARGE(1);

        final int id;

        private Base(final int id) {
            this.id = id;
        }
    }

    public static enum Pattern implements StringRepresentable, TooltipProvider {
        KOB("kob", TropicalFish.Base.SMALL, 0),
        SUNSTREAK("sunstreak", TropicalFish.Base.SMALL, 1),
        SNOOPER("snooper", TropicalFish.Base.SMALL, 2),
        DASHER("dasher", TropicalFish.Base.SMALL, 3),
        BRINELY("brinely", TropicalFish.Base.SMALL, 4),
        SPOTTY("spotty", TropicalFish.Base.SMALL, 5),
        FLOPPER("flopper", TropicalFish.Base.LARGE, 0),
        STRIPEY("stripey", TropicalFish.Base.LARGE, 1),
        GLITTER("glitter", TropicalFish.Base.LARGE, 2),
        BLOCKFISH("blockfish", TropicalFish.Base.LARGE, 3),
        BETTY("betty", TropicalFish.Base.LARGE, 4),
        CLAYFISH("clayfish", TropicalFish.Base.LARGE, 5);

        public static final Codec<TropicalFish.Pattern> CODEC = StringRepresentable.fromEnum(TropicalFish.Pattern::values);
        private static final IntFunction<TropicalFish.Pattern> BY_ID = ByIdMap.sparse(TropicalFish.Pattern::getPackedId, values(), KOB);
        public static final StreamCodec<ByteBuf, TropicalFish.Pattern> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, TropicalFish.Pattern::getPackedId);
        private final String name;
        private final Component displayName;
        private final TropicalFish.Base base;
        private final int packedId;

        private Pattern(final String name, final TropicalFish.Base base, final int id) {
            this.name = name;
            this.base = base;
            this.packedId = base.id | id << 8;
            this.displayName = Component.translatable("entity.minecraft.tropical_fish.type." + this.name);
        }

        public static TropicalFish.Pattern byId(int packedId) {
            return BY_ID.apply(packedId);
        }

        public TropicalFish.Base base() {
            return this.base;
        }

        public int getPackedId() {
            return this.packedId;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public Component displayName() {
            return this.displayName;
        }

        @Override
        public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
            DyeColor dyeColor = componentGetter.getOrDefault(DataComponents.TROPICAL_FISH_BASE_COLOR, TropicalFish.DEFAULT_VARIANT.baseColor());
            DyeColor dyeColor1 = componentGetter.getOrDefault(DataComponents.TROPICAL_FISH_PATTERN_COLOR, TropicalFish.DEFAULT_VARIANT.patternColor());
            ChatFormatting[] chatFormattings = new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.GRAY};
            int index = TropicalFish.COMMON_VARIANTS.indexOf(new TropicalFish.Variant(this, dyeColor, dyeColor1));
            if (index != -1) {
                tooltipAdder.accept(Component.translatable(TropicalFish.getPredefinedName(index)).withStyle(chatFormattings));
            } else {
                tooltipAdder.accept(this.displayName.plainCopy().withStyle(chatFormattings));
                MutableComponent mutableComponent = Component.translatable("color.minecraft." + dyeColor.getName());
                if (dyeColor != dyeColor1) {
                    mutableComponent.append(", ").append(Component.translatable("color.minecraft." + dyeColor1.getName()));
                }

                mutableComponent.withStyle(chatFormattings);
                tooltipAdder.accept(mutableComponent);
            }
        }
    }

    static class TropicalFishGroupData extends AbstractSchoolingFish.SchoolSpawnGroupData {
        final TropicalFish.Variant variant;

        TropicalFishGroupData(TropicalFish leader, TropicalFish.Variant variant) {
            super(leader);
            this.variant = variant;
        }
    }

    public record Variant(TropicalFish.Pattern pattern, DyeColor baseColor, DyeColor patternColor) {
        public static final Codec<TropicalFish.Variant> CODEC = Codec.INT.xmap(TropicalFish.Variant::new, TropicalFish.Variant::getPackedId);

        public Variant(int id) {
            this(TropicalFish.getPattern(id), TropicalFish.getBaseColor(id), TropicalFish.getPatternColor(id));
        }

        public int getPackedId() {
            return TropicalFish.packVariant(this.pattern, this.baseColor, this.patternColor);
        }
    }
}
