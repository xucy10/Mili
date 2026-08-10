package net.minecraft.world.item;

import com.google.common.collect.ImmutableBiMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopperBlocks;

public record WeatheringCopperItems(
    Item unaffected, Item exposed, Item weathered, Item oxidized, Item waxed, Item waxedExposed, Item waxedWeathered, Item waxedOxidized
) {
    public static WeatheringCopperItems create(WeatheringCopperBlocks blocks, Function<Block, Item> itemGetter) {
        return new WeatheringCopperItems(
            itemGetter.apply(blocks.unaffected()),
            itemGetter.apply(blocks.exposed()),
            itemGetter.apply(blocks.weathered()),
            itemGetter.apply(blocks.oxidized()),
            itemGetter.apply(blocks.waxed()),
            itemGetter.apply(blocks.waxedExposed()),
            itemGetter.apply(blocks.waxedWeathered()),
            itemGetter.apply(blocks.waxedOxidized())
        );
    }

    public ImmutableBiMap<Item, Item> waxedMapping() {
        return ImmutableBiMap.of(
            this.unaffected, this.waxed, this.exposed, this.waxedExposed, this.weathered, this.waxedWeathered, this.oxidized, this.waxedOxidized
        );
    }

    public void forEach(Consumer<Item> action) {
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
