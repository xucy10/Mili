package net.minecraft.world.item;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;

public class DyeItem extends Item implements SignApplicator {
    private static final Map<DyeColor, DyeItem> ITEM_BY_COLOR = Maps.newEnumMap(DyeColor.class);
    private final DyeColor dyeColor;

    public DyeItem(DyeColor dyeColor, Item.Properties properties) {
        super(properties);
        this.dyeColor = dyeColor;
        ITEM_BY_COLOR.put(dyeColor, this);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof Sheep sheep && sheep.isAlive() && !sheep.isSheared() && sheep.getColor() != this.dyeColor) {
            sheep.level().playSound(player, sheep, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!player.level().isClientSide()) {
                // CraftBukkit start
                byte bColor = (byte) this.dyeColor.getId();
                org.bukkit.event.entity.SheepDyeWoolEvent event = new org.bukkit.event.entity.SheepDyeWoolEvent((org.bukkit.entity.Sheep) sheep.getBukkitEntity(), org.bukkit.DyeColor.getByWoolData(bColor), (org.bukkit.entity.Player) player.getBukkitEntity());
                sheep.level().getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return InteractionResult.PASS;
                }

                sheep.setColor(DyeColor.byId((byte) event.getColor().getWoolData()));
                // CraftBukkit end
                stack.shrink(1);
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    public DyeColor getDyeColor() {
        return this.dyeColor;
    }

    public static DyeItem byColor(DyeColor color) {
        return ITEM_BY_COLOR.get(color);
    }

    @Override
    public boolean tryApplyToSign(Level level, SignBlockEntity sign, boolean isFront, Player player) {
        if (sign.updateText(signText -> signText.setColor(this.getDyeColor()), isFront)) {
            level.playSound(null, sign.getBlockPos(), SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        } else {
            return false;
        }
    }
}
