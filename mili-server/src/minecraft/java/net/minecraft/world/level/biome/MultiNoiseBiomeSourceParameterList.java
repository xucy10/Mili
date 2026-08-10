package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

public class MultiNoiseBiomeSourceParameterList {
    public static final Codec<MultiNoiseBiomeSourceParameterList> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                MultiNoiseBiomeSourceParameterList.Preset.CODEC.fieldOf("preset").forGetter(parameterList -> parameterList.preset),
                RegistryOps.retrieveGetter(Registries.BIOME)
            )
            .apply(instance, MultiNoiseBiomeSourceParameterList::new)
    );
    public static final Codec<Holder<MultiNoiseBiomeSourceParameterList>> CODEC = RegistryFileCodec.create(
        Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, DIRECT_CODEC
    );
    private final MultiNoiseBiomeSourceParameterList.Preset preset;
    private final Climate.ParameterList<Holder<Biome>> parameters;

    public MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset preset, HolderGetter<Biome> biomes) {
        this.preset = preset;
        this.parameters = preset.provider.apply(biomes::getOrThrow);
    }

    public Climate.ParameterList<Holder<Biome>> parameters() {
        return this.parameters;
    }

    public static Map<MultiNoiseBiomeSourceParameterList.Preset, Climate.ParameterList<ResourceKey<Biome>>> knownPresets() {
        return MultiNoiseBiomeSourceParameterList.Preset.BY_NAME
            .values()
            .stream()
            .collect(Collectors.toMap(preset -> (MultiNoiseBiomeSourceParameterList.Preset)preset, preset -> preset.provider().apply(biomeKey -> biomeKey)));
    }

    public record Preset(Identifier id, MultiNoiseBiomeSourceParameterList.Preset.SourceProvider provider) {
        public static final MultiNoiseBiomeSourceParameterList.Preset NETHER = new MultiNoiseBiomeSourceParameterList.Preset(
            Identifier.withDefaultNamespace("nether"),
            new MultiNoiseBiomeSourceParameterList.Preset.SourceProvider() {
                @Override
                public <T> Climate.ParameterList<T> apply(Function<ResourceKey<Biome>, T> valueGetter) {
                    return new Climate.ParameterList<>(
                        List.of(
                            Pair.of(Climate.parameters(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), valueGetter.apply(Biomes.NETHER_WASTES)),
                            Pair.of(Climate.parameters(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), valueGetter.apply(Biomes.SOUL_SAND_VALLEY)),
                            Pair.of(Climate.parameters(0.4F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), valueGetter.apply(Biomes.CRIMSON_FOREST)),
                            Pair.of(Climate.parameters(0.0F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.375F), valueGetter.apply(Biomes.WARPED_FOREST)),
                            Pair.of(Climate.parameters(-0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.175F), valueGetter.apply(Biomes.BASALT_DELTAS))
                        )
                    );
                }
            }
        );
        public static final MultiNoiseBiomeSourceParameterList.Preset OVERWORLD = new MultiNoiseBiomeSourceParameterList.Preset(
            Identifier.withDefaultNamespace("overworld"), new MultiNoiseBiomeSourceParameterList.Preset.SourceProvider() {
                @Override
                public <T> Climate.ParameterList<T> apply(Function<ResourceKey<Biome>, T> valueGetter) {
                    return MultiNoiseBiomeSourceParameterList.Preset.generateOverworldBiomes(valueGetter);
                }
            }
        );
        static final Map<Identifier, MultiNoiseBiomeSourceParameterList.Preset> BY_NAME = Stream.of(NETHER, OVERWORLD)
            .collect(Collectors.toMap(MultiNoiseBiomeSourceParameterList.Preset::id, preset -> (MultiNoiseBiomeSourceParameterList.Preset)preset));
        public static final Codec<MultiNoiseBiomeSourceParameterList.Preset> CODEC = Identifier.CODEC
            .flatXmap(
                resourceName -> Optional.ofNullable(BY_NAME.get(resourceName))
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown preset: " + resourceName)),
                preset -> DataResult.success(preset.id)
            );

        static <T> Climate.ParameterList<T> generateOverworldBiomes(Function<ResourceKey<Biome>, T> valueGetter) {
            Builder<Pair<Climate.ParameterPoint, T>> builder = ImmutableList.builder();
            new OverworldBiomeBuilder().addBiomes(pair -> builder.add(pair.mapSecond(valueGetter)));
            return new Climate.ParameterList<>(builder.build());
        }

        public Stream<ResourceKey<Biome>> usedBiomes() {
            return this.provider.apply(biomeKey -> biomeKey).values().stream().map(Pair::getSecond).distinct();
        }

        @FunctionalInterface
        interface SourceProvider {
            <T> Climate.ParameterList<T> apply(Function<ResourceKey<Biome>, T> valueGetter);
        }
    }
}
