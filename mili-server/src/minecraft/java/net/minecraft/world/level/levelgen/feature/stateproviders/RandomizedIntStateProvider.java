package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public class RandomizedIntStateProvider extends BlockStateProvider {
    public static final MapCodec<RandomizedIntStateProvider> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                BlockStateProvider.CODEC.fieldOf("source").forGetter(provider -> provider.source),
                Codec.STRING.fieldOf("property").forGetter(provider -> provider.propertyName),
                IntProvider.CODEC.fieldOf("values").forGetter(provider -> provider.values)
            )
            .apply(instance, RandomizedIntStateProvider::new)
    );
    private final BlockStateProvider source;
    private final String propertyName;
    private @Nullable IntegerProperty property;
    private final IntProvider values;

    public RandomizedIntStateProvider(BlockStateProvider source, IntegerProperty property, IntProvider values) {
        this.source = source;
        this.property = property;
        this.propertyName = property.getName();
        this.values = values;
        Collection<Integer> possibleValues = property.getPossibleValues();

        for (int value = values.getMinValue(); value <= values.getMaxValue(); value++) {
            if (!possibleValues.contains(value)) {
                throw new IllegalArgumentException("Property value out of range: " + property.getName() + ": " + value);
            }
        }
    }

    public RandomizedIntStateProvider(BlockStateProvider source, String propertyName, IntProvider values) {
        this.source = source;
        this.propertyName = propertyName;
        this.values = values;
    }

    @Override
    protected BlockStateProviderType<?> type() {
        return BlockStateProviderType.RANDOMIZED_INT_STATE_PROVIDER;
    }

    @Override
    public BlockState getState(RandomSource random, BlockPos pos) {
        BlockState state = this.source.getState(random, pos);
        if (this.property == null || !state.hasProperty(this.property)) {
            IntegerProperty integerProperty = findProperty(state, this.propertyName);
            if (integerProperty == null) {
                return state;
            }

            this.property = integerProperty;
        }

        return state.setValue(this.property, this.values.sample(random));
    }

    private static @Nullable IntegerProperty findProperty(BlockState state, String propertyName) {
        Collection<Property<?>> properties = state.getProperties();
        Optional<IntegerProperty> optional = properties.stream()
            .filter(property -> property.getName().equals(propertyName))
            .filter(property -> property instanceof IntegerProperty)
            .map(property -> (IntegerProperty)property)
            .findAny();
        return optional.orElse(null);
    }
}
