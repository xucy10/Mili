package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;

public record ChangeItemDamage(LevelBasedValue amount) implements EnchantmentEntityEffect {
    public static final MapCodec<ChangeItemDamage> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(LevelBasedValue.CODEC.fieldOf("amount").forGetter(changeItemDamage -> changeItemDamage.amount))
            .apply(instance, ChangeItemDamage::new)
    );

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        ItemStack itemStack = item.itemStack();
        if (itemStack.has(DataComponents.MAX_DAMAGE) && itemStack.has(DataComponents.DAMAGE)) {
            //ServerPlayer serverPlayer1 = item.owner() instanceof ServerPlayer serverPlayer ? serverPlayer : null; // Paper - EntityDamageItemEvent - always pass in entity
            int i = (int)this.amount.calculate(enchantmentLevel);
            itemStack.hurtAndBreak(i, level, item.owner(), item.onBreak()); // Paper - EntityDamageItemEvent - always pass in entity
        }
    }

    @Override
    public MapCodec<ChangeItemDamage> codec() {
        return CODEC;
    }
}
