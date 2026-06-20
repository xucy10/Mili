package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.apache.commons.lang3.function.TriFunction;

public record WeatheringCopperBlocks(
    Block unaffected, Block exposed, Block weathered, Block oxidized, Block waxed, Block waxedExposed, Block waxedWeathered, Block waxedOxidized
) {
    public static <WaxedBlock extends Block, WeatheringBlock extends Block & WeatheringCopper> WeatheringCopperBlocks create(
        String name,
        TriFunction<String, Function<BlockBehaviour.Properties, Block>, BlockBehaviour.Properties, Block> register,
        Function<BlockBehaviour.Properties, WaxedBlock> block,
        BiFunction<WeatheringCopper.WeatherState, BlockBehaviour.Properties, WeatheringBlock> weatheringBlock,
        Function<WeatheringCopper.WeatherState, BlockBehaviour.Properties> properties
    ) {
        return new WeatheringCopperBlocks(
            register.apply(
                name,
                properties1 -> weatheringBlock.apply(WeatheringCopper.WeatherState.UNAFFECTED, properties1),
                properties.apply(WeatheringCopper.WeatherState.UNAFFECTED)
            ),
            register.apply(
                "exposed_" + name,
                properties1 -> weatheringBlock.apply(WeatheringCopper.WeatherState.EXPOSED, properties1),
                properties.apply(WeatheringCopper.WeatherState.EXPOSED)
            ),
            register.apply(
                "weathered_" + name,
                properties1 -> weatheringBlock.apply(WeatheringCopper.WeatherState.WEATHERED, properties1),
                properties.apply(WeatheringCopper.WeatherState.WEATHERED)
            ),
            register.apply(
                "oxidized_" + name,
                properties1 -> weatheringBlock.apply(WeatheringCopper.WeatherState.OXIDIZED, properties1),
                properties.apply(WeatheringCopper.WeatherState.OXIDIZED)
            ),
            register.apply("waxed_" + name, block::apply, properties.apply(WeatheringCopper.WeatherState.UNAFFECTED)),
            register.apply("waxed_exposed_" + name, block::apply, properties.apply(WeatheringCopper.WeatherState.EXPOSED)),
            register.apply("waxed_weathered_" + name, block::apply, properties.apply(WeatheringCopper.WeatherState.WEATHERED)),
            register.apply("waxed_oxidized_" + name, block::apply, properties.apply(WeatheringCopper.WeatherState.OXIDIZED))
        );
    }

    public ImmutableBiMap<Block, Block> weatheringMapping() {
        return ImmutableBiMap.of(this.unaffected, this.exposed, this.exposed, this.weathered, this.weathered, this.oxidized);
    }

    public ImmutableBiMap<Block, Block> waxedMapping() {
        return ImmutableBiMap.of(
            this.unaffected, this.waxed, this.exposed, this.waxedExposed, this.weathered, this.waxedWeathered, this.oxidized, this.waxedOxidized
        );
    }

    public ImmutableList<Block> asList() {
        return ImmutableList.of(
            this.unaffected, this.waxed, this.exposed, this.waxedExposed, this.weathered, this.waxedWeathered, this.oxidized, this.waxedOxidized
        );
    }

    public void forEach(Consumer<Block> action) {
        action.accept(this.unaffected);
        action.accept(this.exposed);
        action.accept(this.weathered);
        action.accept(this.oxidized);
        action.accept(this.waxed);
        action.accept(this.waxedExposed);
        action.accept(this.waxedWeathered);
        action.accept(this.waxedOxidized);
    }
}
