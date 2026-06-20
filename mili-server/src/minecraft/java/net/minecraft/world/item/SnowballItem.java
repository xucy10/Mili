package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.level.Level;

public class SnowballItem extends Item implements ProjectileItem {
    public static float PROJECTILE_SHOOT_POWER = 1.5F;

    public SnowballItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        // CraftBukkit start - moved down
        if (level instanceof ServerLevel serverLevel) {
            // Paper start - PlayerLaunchProjectileEvent
            final Projectile.Delayed<Snowball> snowball = Projectile.spawnProjectileFromRotationDelayed(Snowball::new, serverLevel, itemInHand, player, 0.0F, SnowballItem.PROJECTILE_SHOOT_POWER, 1.0F);
            com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event = new com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent((org.bukkit.entity.Player) player.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemInHand), (org.bukkit.entity.Projectile) snowball.projectile().getBukkitEntity());
            if (event.callEvent() && snowball.attemptSpawn()) {
                player.awardStat(Stats.ITEM_USED.get(this));
                if (event.shouldConsume()) {
                    itemInHand.consume(1, player);
                } else {
                    player.containerMenu.forceHeldSlot(hand);
                }
                // Paper end - PlayerLaunchProjectileEvent

                level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.SNOWBALL_THROW,
                    SoundSource.NEUTRAL,
                    0.5F,
                    0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
                );
                // Paper start - PlayerLaunchProjectileEvent - return fail
            } else {
                player.containerMenu.forceHeldSlot(hand);
                return InteractionResult.FAIL;
            }
            // Paper end- PlayerLaunchProjectileEvent - return fail
            // CraftBukkit end
        }

        // Paper - PlayerLaunchProjectileEvent - moved up
        // itemInHand.consume(1, player); // CraftBukkit - moved up
        return InteractionResult.SUCCESS;
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        return new Snowball(level, pos.x(), pos.y(), pos.z(), stack);
    }
}
