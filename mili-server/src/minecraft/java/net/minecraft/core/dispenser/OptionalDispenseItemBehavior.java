package net.minecraft.core.dispenser;

import net.minecraft.world.level.block.LevelEvent;

public abstract class OptionalDispenseItemBehavior extends DefaultDispenseItemBehavior {
    private boolean success = true;

    public boolean isSuccess() {
        return this.success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    protected void playSound(BlockSource blockSource) {
        blockSource.level().levelEvent(this.isSuccess() ? LevelEvent.SOUND_DISPENSER_DISPENSE : LevelEvent.SOUND_DISPENSER_FAIL, blockSource.pos(), 0);
    }
}
