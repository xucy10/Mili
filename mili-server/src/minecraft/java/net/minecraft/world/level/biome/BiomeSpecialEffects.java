package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

public record BiomeSpecialEffects(
    int waterColor,
    Optional<Integer> foliageColorOverride,
    Optional<Integer> dryFoliageColorOverride,
    Optional<Integer> grassColorOverride,
    BiomeSpecialEffects.GrassColorModifier grassColorModifier
) {
    public static final Codec<BiomeSpecialEffects> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ExtraCodecs.STRING_RGB_COLOR.fieldOf("water_color").forGetter(BiomeSpecialEffects::waterColor),
                ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("foliage_color").forGetter(BiomeSpecialEffects::foliageColorOverride),
                ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("dry_foliage_color").forGetter(BiomeSpecialEffects::dryFoliageColorOverride),
                ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("grass_color").forGetter(BiomeSpecialEffects::grassColorOverride),
                BiomeSpecialEffects.GrassColorModifier.CODEC
                    .optionalFieldOf("grass_color_modifier", BiomeSpecialEffects.GrassColorModifier.NONE)
                    .forGetter(BiomeSpecialEffects::grassColorModifier)
            )
            .apply(instance, BiomeSpecialEffects::new)
    );

    public static class Builder {
        private OptionalInt waterColor = OptionalInt.empty();
        private Optional<Integer> foliageColorOverride = Optional.empty();
        private Optional<Integer> dryFoliageColorOverride = Optional.empty();
        private Optional<Integer> grassColorOverride = Optional.empty();
        private BiomeSpecialEffects.GrassColorModifier grassColorModifier = BiomeSpecialEffects.GrassColorModifier.NONE;

        public BiomeSpecialEffects.Builder waterColor(int waterColor) {
            this.waterColor = OptionalInt.of(waterColor);
            return this;
        }

        public BiomeSpecialEffects.Builder foliageColorOverride(int foliageColorOverride) {
            this.foliageColorOverride = Optional.of(foliageColorOverride);
            return this;
        }

        public BiomeSpecialEffects.Builder dryFoliageColorOverride(int dryFoliageColorOverride) {
            this.dryFoliageColorOverride = Optional.of(dryFoliageColorOverride);
            return this;
        }

        public BiomeSpecialEffects.Builder grassColorOverride(int grassColorOverride) {
            this.grassColorOverride = Optional.of(grassColorOverride);
            return this;
        }

        public BiomeSpecialEffects.Builder grassColorModifier(BiomeSpecialEffects.GrassColorModifier grassColorModifier) {
            this.grassColorModifier = grassColorModifier;
            return this;
        }

        public BiomeSpecialEffects build() {
            return new BiomeSpecialEffects(
                this.waterColor.orElseThrow(() -> new IllegalStateException("Missing 'water' color.")),
                this.foliageColorOverride,
                this.dryFoliageColorOverride,
                this.grassColorOverride,
                this.grassColorModifier
            );
        }
    }

    public static enum GrassColorModifier implements StringRepresentable {
        NONE("none") {
            @Override
            public int modifyColor(double x, double z, int grassColor) {
                return grassColor;
            }
        },
        DARK_FOREST("dark_forest") {
            @Override
            public int modifyColor(double x, double z, int grassColor) {
                return (grassColor & 16711422) + 2634762 >> 1;
            }
        },
        SWAMP("swamp") {
            @Override
            public int modifyColor(double x, double z, int grassColor) {
                double value = Biome.BIOME_INFO_NOISE.getValue(x * 0.0225, z * 0.0225, false);
                return value < -0.1 ? 5011004 : 6975545;
            }
        };

        private final String name;
        public static final Codec<BiomeSpecialEffects.GrassColorModifier> CODEC = StringRepresentable.fromEnum(BiomeSpecialEffects.GrassColorModifier::values);

        public abstract int modifyColor(double x, double z, int grassColor);

        GrassColorModifier(final String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
