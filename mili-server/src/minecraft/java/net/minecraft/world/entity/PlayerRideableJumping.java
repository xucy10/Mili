package net.minecraft.world.entity;

public interface PlayerRideableJumping extends PlayerRideable {
    void onPlayerJump(int jumpPower);

    boolean canJump();

    void handleStartJump(int jumpPower);

    void handleStopJump();

    default int getJumpCooldown() {
        return 0;
    }

    default float getPlayerJumpPendingScale(int jumpPower) {
        return jumpPower >= 90 ? 1.0F : 0.4F + 0.4F * jumpPower / 90.0F;
    }
}
