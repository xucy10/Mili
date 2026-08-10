package net.minecraft.world.level.storage.loot.functions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.slf4j.Logger;

public class SetItemDamageFunction extends LootItemConditionalFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<SetItemDamageFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    NumberProviders.CODEC.fieldOf("damage").forGetter(setItemDamageFunction -> setItemDamageFunction.damage),
                    Codec.BOOL.fieldOf("add").orElse(false).forGetter(setItemDamageFunction -> setItemDamageFunction.add)
                )
            )
            .apply(instance, SetItemDamageFunction::new)
    );
    private final NumberProvider damage;
    private final boolean add;

    private SetItemDamageFunction(List<LootItemCondition> predicates, NumberProvider damage, boolean add) {
        super(predicates);
        this.damage = damage;
        this.add = add;
    }

    @Override
    public LootItemFunctionType<SetItemDamageFunction> getType() {
        return LootItemFunctions.SET_DAMAGE;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return this.damage.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.isDamageableItem()) {
            int maxDamage = stack.getMaxDamage();
            float f = this.add ? 1.0F - (float)stack.getDamageValue() / maxDamage : 0.0F;
            float f1 = 1.0F - Mth.clamp(this.damage.getFloat(context) + f, 0.0F, 1.0F);
            stack.setDamageValue(Mth.floor(f1 * maxDamage));
        } else {
            LOGGER.warn("Couldn't set damage of loot item {}", stack);
        }

        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setDamage(NumberProvider damageValue) {
        return simpleBuilder(list -> new SetItemDamageFunction(list, damageValue, false));
    }

    public static LootItemConditionalFunction.Builder<?> setDamage(NumberProvider damageValue, boolean add) {
        return simpleBuilder(list -> new SetItemDamageFunction(list, damageValue, add));
    }
}
