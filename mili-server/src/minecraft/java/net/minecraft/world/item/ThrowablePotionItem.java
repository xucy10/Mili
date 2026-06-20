package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.level.Level;

public abstract class ThrowablePotionItem extends PotionItem implements ProjectileItem {
    public static float PROJECTILE_SHOOT_POWER = 0.5F;

    public ThrowablePotionItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            // Paper start - PlayerLaunchProjectileEvent
            final Projectile.Delayed<AbstractThrownPotion> thrownPotion = Projectile.spawnProjectileFromRotationDelayed(this::createPotion, serverLevel, itemInHand, player, -20.0F, PROJECTILE_SHOOT_POWER, 1.0F);
            com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event = new com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent((org.bukkit.entity.Player) player.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemInHand), (org.bukkit.entity.Projectile) thrownPotion.projectile().getBukkitEntity());
            if (event.callEvent() && thrownPotion.attemptSpawn()) {
                if (event.shouldConsume()) {
                    itemInHand.consume(1, player);
                } else {
                    player.containerMenu.forceHeldSlot(hand);
                }
            } else {
                player.containerMenu.forceHeldSlot(hand);
                return InteractionResult.FAIL;
            }
            // Paper end - PlayerLaunchProjectileEvent
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        // Paper - PlayerLaunchProjectileEvent - move up
        return InteractionResult.SUCCESS;
    }

    protected abstract AbstractThrownPotion createPotion(ServerLevel level, LivingEntity entity, ItemStack stack);

    protected abstract AbstractThrownPotion createPotion(Level level, Position position, ItemStack stack);

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        return this.createPotion(level, pos, stack);
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .uncertainty(ProjectileItem.DispenseConfig.DEFAULT.uncertainty() * 0.5F)
            .power(ProjectileItem.DispenseConfig.DEFAULT.power() * 1.25F)
            .build();
    }
}
