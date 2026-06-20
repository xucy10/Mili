package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public abstract class ProjectileWeaponItem extends Item {
    public static final Predicate<ItemStack> ARROW_ONLY = stack -> stack.is(ItemTags.ARROWS);
    public static final Predicate<ItemStack> ARROW_OR_FIREWORK = ARROW_ONLY.or(stack -> stack.is(Items.FIREWORK_ROCKET));

    public ProjectileWeaponItem(Item.Properties properties) {
        super(properties);
    }

    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return this.getAllSupportedProjectiles();
    }

    public abstract Predicate<ItemStack> getAllSupportedProjectiles();

    public static ItemStack getHeldProjectile(LivingEntity shooter, Predicate<ItemStack> isAmmo) {
        if (isAmmo.test(shooter.getItemInHand(InteractionHand.OFF_HAND))) {
            return shooter.getItemInHand(InteractionHand.OFF_HAND);
        } else {
            return isAmmo.test(shooter.getItemInHand(InteractionHand.MAIN_HAND)) ? shooter.getItemInHand(InteractionHand.MAIN_HAND) : ItemStack.EMPTY;
        }
    }

    public abstract int getDefaultProjectileRange();

    protected void shoot(
        ServerLevel level,
        LivingEntity shooter,
        InteractionHand hand,
        ItemStack weapon,
        List<ItemStack> projectileItems,
        float velocity,
        float inaccuracy,
        boolean isCrit,
        @Nullable LivingEntity target
        ,float drawStrength // Paper - Pass draw strength
    ) {
        float f = EnchantmentHelper.processProjectileSpread(level, weapon, shooter, 0.0F);
        float f1 = projectileItems.size() == 1 ? 0.0F : 2.0F * f / (projectileItems.size() - 1);
        float f2 = (projectileItems.size() - 1) % 2 * f1 / 2.0F;
        float f3 = 1.0F;

        for (int i = 0; i < projectileItems.size(); i++) {
            ItemStack itemStack = projectileItems.get(i);
            if (!itemStack.isEmpty()) {
                float f4 = f2 + f3 * ((i + 1) / 2) * f1;
                f3 = -f3;
                int i1 = i;
                // CraftBukkit start
                Projectile projectile = this.createProjectile(level, shooter, weapon, itemStack, isCrit);
                this.shootProjectile(shooter, projectile, i1, velocity, inaccuracy, f4, target);

                org.bukkit.event.entity.EntityShootBowEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityShootBowEvent(shooter, weapon, itemStack, projectile, hand, drawStrength, true);
                if (event.isCancelled()) {
                    event.getProjectile().remove();
                    return;
                }

                if (event.getProjectile() == projectile.getBukkitEntity()) {
                    if (Projectile.spawnProjectile(
                        projectile,
                        level,
                        itemStack
                    ).isRemoved()) {
                        return;
                    }
                }
                // CraftBukkit end
                weapon.hurtAndBreak(this.getDurabilityUse(itemStack), shooter, hand.asEquipmentSlot());
                if (weapon.isEmpty()) {
                    break;
                }
            }
        }
    }

    protected int getDurabilityUse(ItemStack stack) {
        return 1;
    }

    protected abstract void shootProjectile(
        LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, @Nullable LivingEntity target
    );

    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
        ArrowItem arrowItem1 = ammo.getItem() instanceof ArrowItem arrowItem ? arrowItem : (ArrowItem)Items.ARROW;
        AbstractArrow abstractArrow = arrowItem1.createArrow(level, ammo, shooter, weapon);
        if (isCrit) {
            abstractArrow.setCritArrow(true);
        }

        return abstractArrow;
    }

    protected static List<ItemStack> draw(ItemStack weapon, ItemStack ammo, LivingEntity shooter) {
        // Paper start
        return draw(weapon, ammo, shooter, true);
    }
    protected static List<ItemStack> draw(ItemStack weapon, ItemStack ammo, LivingEntity shooter, boolean consume) {
        // Paper end
        if (ammo.isEmpty()) {
            return List.of();
        } else {
            int i = shooter.level() instanceof ServerLevel serverLevel ? EnchantmentHelper.processProjectileCount(serverLevel, weapon, shooter, 1) : 1;
            List<ItemStack> list = new ArrayList<>(i);
            ItemStack itemStack = ammo.copy();

            for (int i1 = 0; i1 < i; i1++) {
                ItemStack itemStack1 = useAmmo(weapon, i1 == 0 ? ammo : itemStack, shooter, i1 > 0 || !consume); // Paper
                if (!itemStack1.isEmpty()) {
                    list.add(itemStack1);
                }
            }

            return list;
        }
    }

    protected static ItemStack useAmmo(ItemStack weapon, ItemStack ammo, LivingEntity shooter, boolean intangible) {
        int i = !intangible && !shooter.hasInfiniteMaterials() && shooter.level() instanceof ServerLevel serverLevel
            ? EnchantmentHelper.processAmmoUse(serverLevel, weapon, ammo, 1)
            : 0;
        if (i > ammo.getCount()) {
            return ItemStack.EMPTY;
        } else if (i == 0) {
            ItemStack itemStack = ammo.copyWithCount(1);
            itemStack.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            return itemStack;
        } else {
            ItemStack itemStack = ammo.split(i);
            if (ammo.isEmpty() && shooter instanceof Player player) {
                player.getInventory().removeItem(ammo);
            }

            return itemStack;
        }
    }
}
