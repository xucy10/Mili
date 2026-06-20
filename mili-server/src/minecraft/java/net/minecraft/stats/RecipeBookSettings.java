package net.minecraft.stats;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.UnaryOperator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.inventory.RecipeBookType;

public final class RecipeBookSettings {
    public static final StreamCodec<FriendlyByteBuf, RecipeBookSettings> STREAM_CODEC = StreamCodec.composite(
        RecipeBookSettings.TypeSettings.STREAM_CODEC,
        settings -> settings.crafting,
        RecipeBookSettings.TypeSettings.STREAM_CODEC,
        settings -> settings.furnace,
        RecipeBookSettings.TypeSettings.STREAM_CODEC,
        settings -> settings.blastFurnace,
        RecipeBookSettings.TypeSettings.STREAM_CODEC,
        settings -> settings.smoker,
        RecipeBookSettings::new
    );
    public static final MapCodec<RecipeBookSettings> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                RecipeBookSettings.TypeSettings.CRAFTING_MAP_CODEC.forGetter(settings -> settings.crafting),
                RecipeBookSettings.TypeSettings.FURNACE_MAP_CODEC.forGetter(settings -> settings.furnace),
                RecipeBookSettings.TypeSettings.BLAST_FURNACE_MAP_CODEC.forGetter(settings -> settings.blastFurnace),
                RecipeBookSettings.TypeSettings.SMOKER_MAP_CODEC.forGetter(settings -> settings.smoker)
            )
            .apply(instance, RecipeBookSettings::new)
    );
    private RecipeBookSettings.TypeSettings crafting;
    private RecipeBookSettings.TypeSettings furnace;
    private RecipeBookSettings.TypeSettings blastFurnace;
    private RecipeBookSettings.TypeSettings smoker;

    public RecipeBookSettings() {
        this(
            RecipeBookSettings.TypeSettings.DEFAULT,
            RecipeBookSettings.TypeSettings.DEFAULT,
            RecipeBookSettings.TypeSettings.DEFAULT,
            RecipeBookSettings.TypeSettings.DEFAULT
        );
    }

    private RecipeBookSettings(
        RecipeBookSettings.TypeSettings crafting,
        RecipeBookSettings.TypeSettings furnace,
        RecipeBookSettings.TypeSettings blastFurnace,
        RecipeBookSettings.TypeSettings smoker
    ) {
        this.crafting = crafting;
        this.furnace = furnace;
        this.blastFurnace = blastFurnace;
        this.smoker = smoker;
    }

    @VisibleForTesting
    public RecipeBookSettings.TypeSettings getSettings(RecipeBookType type) {
        return switch (type) {
            case CRAFTING -> this.crafting;
            case FURNACE -> this.furnace;
            case BLAST_FURNACE -> this.blastFurnace;
            case SMOKER -> this.smoker;
        };
    }

    private void updateSettings(RecipeBookType type, UnaryOperator<RecipeBookSettings.TypeSettings> updater) {
        switch (type) {
            case CRAFTING:
                this.crafting = updater.apply(this.crafting);
                break;
            case FURNACE:
                this.furnace = updater.apply(this.furnace);
                break;
            case BLAST_FURNACE:
                this.blastFurnace = updater.apply(this.blastFurnace);
                break;
            case SMOKER:
                this.smoker = updater.apply(this.smoker);
        }
    }

    public boolean isOpen(RecipeBookType bookType) {
        return this.getSettings(bookType).open;
    }

    public void setOpen(RecipeBookType bookType, boolean _open) {
        this.updateSettings(bookType, settings -> settings.setOpen(_open));
    }

    public boolean isFiltering(RecipeBookType bookType) {
        return this.getSettings(bookType).filtering;
    }

    public void setFiltering(RecipeBookType bookType, boolean filtering) {
        this.updateSettings(bookType, settings -> settings.setFiltering(filtering));
    }

    public RecipeBookSettings copy() {
        return new RecipeBookSettings(this.crafting, this.furnace, this.blastFurnace, this.smoker);
    }

    public void replaceFrom(RecipeBookSettings other) {
        this.crafting = other.crafting;
        this.furnace = other.furnace;
        this.blastFurnace = other.blastFurnace;
        this.smoker = other.smoker;
    }

    public record TypeSettings(boolean open, boolean filtering) {
        public static final RecipeBookSettings.TypeSettings DEFAULT = new RecipeBookSettings.TypeSettings(false, false);
        public static final MapCodec<RecipeBookSettings.TypeSettings> CRAFTING_MAP_CODEC = codec("isGuiOpen", "isFilteringCraftable");
        public static final MapCodec<RecipeBookSettings.TypeSettings> FURNACE_MAP_CODEC = codec("isFurnaceGuiOpen", "isFurnaceFilteringCraftable");
        public static final MapCodec<RecipeBookSettings.TypeSettings> BLAST_FURNACE_MAP_CODEC = codec(
            "isBlastingFurnaceGuiOpen", "isBlastingFurnaceFilteringCraftable"
        );
        public static final MapCodec<RecipeBookSettings.TypeSettings> SMOKER_MAP_CODEC = codec("isSmokerGuiOpen", "isSmokerFilteringCraftable");
        public static final StreamCodec<ByteBuf, RecipeBookSettings.TypeSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            RecipeBookSettings.TypeSettings::open,
            ByteBufCodecs.BOOL,
            RecipeBookSettings.TypeSettings::filtering,
            RecipeBookSettings.TypeSettings::new
        );

        @Override
        public String toString() {
            return "[open=" + this.open + ", filtering=" + this.filtering + "]";
        }

        public RecipeBookSettings.TypeSettings setOpen(boolean _open) {
            return new RecipeBookSettings.TypeSettings(_open, this.filtering);
        }

        public RecipeBookSettings.TypeSettings setFiltering(boolean filtering) {
            return new RecipeBookSettings.TypeSettings(this.open, filtering);
        }

        private static MapCodec<RecipeBookSettings.TypeSettings> codec(String openKey, String filteringKey) {
            return RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Codec.BOOL.optionalFieldOf(openKey, false).forGetter(RecipeBookSettings.TypeSettings::open),
                        Codec.BOOL.optionalFieldOf(filteringKey, false).forGetter(RecipeBookSettings.TypeSettings::filtering)
                    )
                    .apply(instance, RecipeBookSettings.TypeSettings::new)
            );
        }
    }
}
