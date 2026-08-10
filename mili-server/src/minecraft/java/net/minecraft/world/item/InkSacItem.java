package net.minecraft.world.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;

public class InkSacItem extends Item implements SignApplicator {
    public InkSacItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean tryApplyToSign(Level level, SignBlockEntity sign, boolean isFront, Player player) {
        if (sign.updateText(signText -> signText.setHasGlowingText(false), isFront)) {
            level.playSound(null, sign.getBlockPos(), SoundEvents.INK_SAC_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        } else {
            return false;
        }
    }
}
