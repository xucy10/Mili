package net.minecraft.world.entity.vehicle.minecart;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class Minecart extends AbstractMinecart {
    private float rotationOffset;
    private float playerRotationOffset;

    public Minecart(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive() && !this.isVehicle() && (this.level().isClientSide() || player.startRiding(this))) {
            this.playerRotationOffset = this.rotationOffset;
            if (!this.level().isClientSide()) {
                return (InteractionResult)(player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS);
            } else {
                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public Item getDropItem() {
        return Items.MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.MINECART);
    }

    @Override
    public void activateMinecart(ServerLevel level, int x, int y, int z, boolean receivingPower) {
        if (receivingPower) {
            if (this.isVehicle()) {
                this.ejectPassengers();
            }

            if (this.getHurtTime() == 0) {
                this.setHurtDir(-this.getHurtDir());
                this.setHurtTime(10);
                this.setDamage(50.0F);
                this.markHurt();
            }
        }
    }

    @Override
    public boolean isRideable() {
        return true;
    }

    @Override
    public void tick() {
        double d = this.getYRot();
        Vec3 vec3 = this.position();
        super.tick();
        double d1 = (this.getYRot() - d) % 360.0;
        if (this.level().isClientSide() && vec3.distanceTo(this.position()) > 0.01) {
            this.rotationOffset += (float)d1;
            this.rotationOffset %= 360.0F;
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (this.level().isClientSide() && passenger instanceof Player player && player.shouldRotateWithMinecart() && useExperimentalMovement(this.level())) {
            float f = (float)Mth.rotLerp(0.5, (double)this.playerRotationOffset, (double)this.rotationOffset);
            player.setYRot(player.getYRot() - (f - this.playerRotationOffset));
            this.playerRotationOffset = f;
        }
    }
}
