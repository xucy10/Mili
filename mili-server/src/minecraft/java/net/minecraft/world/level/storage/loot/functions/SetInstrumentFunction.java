package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetInstrumentFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetInstrumentFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(TagKey.hashedCodec(Registries.INSTRUMENT).fieldOf("options").forGetter(setInstrumentFunction -> setInstrumentFunction.options))
            .apply(instance, SetInstrumentFunction::new)
    );
    private final TagKey<Instrument> options;

    private SetInstrumentFunction(List<LootItemCondition> predicates, TagKey<Instrument> options) {
        super(predicates);
        this.options = options;
    }

    @Override
    public LootItemFunctionType<SetInstrumentFunction> getType() {
        return LootItemFunctions.SET_INSTRUMENT;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        Registry<Instrument> registry = context.getLevel().registryAccess().lookupOrThrow(Registries.INSTRUMENT);
        Optional<Holder<Instrument>> randomElementOf = registry.getRandomElementOf(this.options, context.getRandom());
        if (randomElementOf.isPresent()) {
            stack.set(DataComponents.INSTRUMENT, new InstrumentComponent(randomElementOf.get()));
        }

        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setInstrumentOptions(TagKey<Instrument> instrumentOptions) {
        return simpleBuilder(list -> new SetInstrumentFunction(list, instrumentOptions));
    }
}
