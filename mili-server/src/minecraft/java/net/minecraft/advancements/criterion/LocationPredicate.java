package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.structure.Structure;

public record LocationPredicate(
    Optional<LocationPredicate.PositionPredicate> position,
    Optional<HolderSet<Biome>> biomes,
    Optional<HolderSet<Structure>> structures,
    Optional<ResourceKey<Level>> dimension,
    Optional<Boolean> smokey,
    Optional<LightPredicate> light,
    Optional<BlockPredicate> block,
    Optional<FluidPredicate> fluid,
    Optional<Boolean> canSeeSky
) {
    public static final Codec<LocationPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                LocationPredicate.PositionPredicate.CODEC.optionalFieldOf("position").forGetter(LocationPredicate::position),
                RegistryCodecs.homogeneousList(Registries.BIOME).optionalFieldOf("biomes").forGetter(LocationPredicate::biomes),
                RegistryCodecs.homogeneousList(Registries.STRUCTURE).optionalFieldOf("structures").forGetter(LocationPredicate::structures),
                ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension").forGetter(LocationPredicate::dimension),
                Codec.BOOL.optionalFieldOf("smokey").forGetter(LocationPredicate::smokey),
                LightPredicate.CODEC.optionalFieldOf("light").forGetter(LocationPredicate::light),
                BlockPredicate.CODEC.optionalFieldOf("block").forGetter(LocationPredicate::block),
                FluidPredicate.CODEC.optionalFieldOf("fluid").forGetter(LocationPredicate::fluid),
                Codec.BOOL.optionalFieldOf("can_see_sky").forGetter(LocationPredicate::canSeeSky)
            )
            .apply(instance, LocationPredicate::new)
    );

    public boolean matches(ServerLevel level, double x, double y, double z) {
        if (this.position.isPresent() && !this.position.get().matches(x, y, z)) {
            return false;
        } else if (this.dimension.isPresent() && this.dimension.get() != (io.papermc.paper.configuration.GlobalConfiguration.get().misc.strictAdvancementDimensionCheck ? level.dimension() : org.bukkit.craftbukkit.util.CraftDimensionUtil.getMainDimensionKey(level))) { // Paper - Add option for strict advancement dimension checks
            return false;
        } else {
            BlockPos blockPos = BlockPos.containing(x, y, z);
            boolean isLoaded = level.isLoaded(blockPos);
            return (!this.biomes.isPresent() || isLoaded && this.biomes.get().contains(level.getBiome(blockPos)))
                && (!this.structures.isPresent() || isLoaded && level.structureManager().getStructureWithPieceAt(blockPos, this.structures.get()).isValid())
                && (!this.smokey.isPresent() || isLoaded && this.smokey.get() == CampfireBlock.isSmokeyPos(level, blockPos))
                && (!this.light.isPresent() || this.light.get().matches(level, blockPos))
                && (!this.block.isPresent() || this.block.get().matches(level, blockPos))
                && (!this.fluid.isPresent() || this.fluid.get().matches(level, blockPos))
                && (!this.canSeeSky.isPresent() || this.canSeeSky.get() == level.canSeeSky(blockPos));
        }
    }

    public static class Builder {
        private MinMaxBounds.Doubles x = MinMaxBounds.Doubles.ANY;
        private MinMaxBounds.Doubles y = MinMaxBounds.Doubles.ANY;
        private MinMaxBounds.Doubles z = MinMaxBounds.Doubles.ANY;
        private Optional<HolderSet<Biome>> biomes = Optional.empty();
        private Optional<HolderSet<Structure>> structures = Optional.empty();
        private Optional<ResourceKey<Level>> dimension = Optional.empty();
        private Optional<Boolean> smokey = Optional.empty();
        private Optional<LightPredicate> light = Optional.empty();
        private Optional<BlockPredicate> block = Optional.empty();
        private Optional<FluidPredicate> fluid = Optional.empty();
        private Optional<Boolean> canSeeSky = Optional.empty();

        public static LocationPredicate.Builder location() {
            return new LocationPredicate.Builder();
        }

        public static LocationPredicate.Builder inBiome(Holder<Biome> biome) {
            return location().setBiomes(HolderSet.direct(biome));
        }

        public static LocationPredicate.Builder inDimension(ResourceKey<Level> dimension) {
            return location().setDimension(dimension);
        }

        public static LocationPredicate.Builder inStructure(Holder<Structure> structure) {
            return location().setStructures(HolderSet.direct(structure));
        }

        public static LocationPredicate.Builder atYLocation(MinMaxBounds.Doubles y) {
            return location().setY(y);
        }

        public LocationPredicate.Builder setX(MinMaxBounds.Doubles x) {
            this.x = x;
            return this;
        }

        public LocationPredicate.Builder setY(MinMaxBounds.Doubles y) {
            this.y = y;
            return this;
        }

        public LocationPredicate.Builder setZ(MinMaxBounds.Doubles z) {
            this.z = z;
            return this;
        }

        public LocationPredicate.Builder setBiomes(HolderSet<Biome> biomes) {
            this.biomes = Optional.of(biomes);
            return this;
        }

        public LocationPredicate.Builder setStructures(HolderSet<Structure> structures) {
            this.structures = Optional.of(structures);
            return this;
        }

        public LocationPredicate.Builder setDimension(ResourceKey<Level> dimension) {
            this.dimension = Optional.of(dimension);
            return this;
        }

        public LocationPredicate.Builder setLight(LightPredicate.Builder light) {
            this.light = Optional.of(light.build());
            return this;
        }

        public LocationPredicate.Builder setBlock(BlockPredicate.Builder block) {
            this.block = Optional.of(block.build());
            return this;
        }

        public LocationPredicate.Builder setFluid(FluidPredicate.Builder fluid) {
            this.fluid = Optional.of(fluid.build());
            return this;
        }

        public LocationPredicate.Builder setSmokey(boolean smokey) {
            this.smokey = Optional.of(smokey);
            return this;
        }

        public LocationPredicate.Builder setCanSeeSky(boolean canSeeSky) {
            this.canSeeSky = Optional.of(canSeeSky);
            return this;
        }

        public LocationPredicate build() {
            Optional<LocationPredicate.PositionPredicate> optional = LocationPredicate.PositionPredicate.of(this.x, this.y, this.z);
            return new LocationPredicate(
                optional, this.biomes, this.structures, this.dimension, this.smokey, this.light, this.block, this.fluid, this.canSeeSky
            );
        }
    }

    record PositionPredicate(MinMaxBounds.Doubles x, MinMaxBounds.Doubles y, MinMaxBounds.Doubles z) {
        public static final Codec<LocationPredicate.PositionPredicate> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    MinMaxBounds.Doubles.CODEC.optionalFieldOf("x", MinMaxBounds.Doubles.ANY).forGetter(LocationPredicate.PositionPredicate::x),
                    MinMaxBounds.Doubles.CODEC.optionalFieldOf("y", MinMaxBounds.Doubles.ANY).forGetter(LocationPredicate.PositionPredicate::y),
                    MinMaxBounds.Doubles.CODEC.optionalFieldOf("z", MinMaxBounds.Doubles.ANY).forGetter(LocationPredicate.PositionPredicate::z)
                )
                .apply(instance, LocationPredicate.PositionPredicate::new)
        );

        static Optional<LocationPredicate.PositionPredicate> of(MinMaxBounds.Doubles x, MinMaxBounds.Doubles y, MinMaxBounds.Doubles z) {
            return x.isAny() && y.isAny() && z.isAny() ? Optional.empty() : Optional.of(new LocationPredicate.PositionPredicate(x, y, z));
        }

        public boolean matches(double x, double y, double z) {
            return this.x.matches(x) && this.y.matches(y) && this.z.matches(z);
        }
    }
}
