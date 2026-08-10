package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ToggleTooltips extends LootItemConditionalFunction {
    public static final MapCodec<ToggleTooltips> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(Codec.unboundedMap(DataComponentType.CODEC, Codec.BOOL).fieldOf("toggles").forGetter(toggleTooltips -> toggleTooltips.values))
            .apply(instance, ToggleTooltips::new)
    );
    private final Map<DataComponentType<?>, Boolean> values;

    private ToggleTooltips(List<LootItemCondition> predicates, Map<DataComponentType<?>, Boolean> values) {
        super(predicates);
        this.values = values;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        stack.update(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT, tooltipDisplay -> {
            for (Entry<DataComponentType<?>, Boolean> entry : this.values.entrySet()) {
                boolean value = entry.getValue();
                tooltipDisplay = tooltipDisplay.withHidden(entry.getKey(), !value);
            }

            return tooltipDisplay;
        });
        return stack;
    }

    @Override
    public LootItemFunctionType<ToggleTooltips> getType() {
        return LootItemFunctions.TOGGLE_TOOLTIPS;
    }
}
