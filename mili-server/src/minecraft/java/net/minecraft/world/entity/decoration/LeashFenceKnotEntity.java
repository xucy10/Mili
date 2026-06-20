package net.minecraft.world.entity.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LeashFenceKnotEntity extends BlockAttachedEntity {
    public static final double OFFSET_Y = 0.375;

    public LeashFenceKnotEntity(EntityType<? extends LeashFenceKnotEntity> type, Level level) {
        super(type, level);
    }

    public LeashFenceKnotEntity(Level level, BlockPos pos) {
        super(EntityType.LEASH_KNOT, level, pos);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void recalculateBoundingBox() {
        this.setPosRaw(this.pos.getX() + 0.5, this.pos.getY() + 0.375, this.pos.getZ() + 0.5);
        double d = this.getType().getWidth() / 2.0;
        double d1 = this.getType().getHeight();
        this.setBoundingBox(new AABB(this.getX() - d, this.getY(), this.getZ() - d, this.getX() + d, this.getY() + d1, this.getZ() + d));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 1024.0;
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity entity) {
        this.playSound(SoundEvents.LEAD_UNTIED, 1.0F, 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        } else {
            if (player.getItemInHand(hand).is(Items.SHEARS)) {
                InteractionResult interactionResult = super.interact(player, hand);
                if (interactionResult instanceof InteractionResult.Success success && success.wasItemInteraction()) {
                    return interactionResult;
                }
            }

            boolean flag = false;

            for (Leashable leashable : Leashable.leashableLeashedTo(player)) {
                if (leashable.canHaveALeashAttachedTo(this) && org.bukkit.craftbukkit.event.CraftEventFactory.handlePlayerLeashEntityEvent(leashable, this, player, hand)) { // Paper - leash event
                    leashable.setLeashedTo(this, true);
                    flag = true;
                }
            }

            boolean flag1 = false;
            if (!flag && !player.isSecondaryUseActive()) {
                for (Leashable leashable1 : Leashable.leashableLeashedTo(this)) {
                    if (leashable1.canHaveALeashAttachedTo(player) && org.bukkit.craftbukkit.event.CraftEventFactory.handlePlayerLeashEntityEvent(leashable1, player, player, hand)) { // Paper - leash event
                        leashable1.setLeashedTo(player, true);
                        flag1 = true;
                    }
                }
            }

            if (!flag && !flag1) {
                return super.interact(player, hand);
            } else {
                this.gameEvent(GameEvent.BLOCK_ATTACH, player);
                this.playSound(SoundEvents.LEAD_TIED);
                return InteractionResult.SUCCESS;
            }
        }
    }

    @Override
    public void notifyLeasheeRemoved(Leashable leashHolder) {
        if (Leashable.leashableLeashedTo(this).isEmpty()) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
        }
    }

    @Override
    public boolean survives() {
        return this.level().getBlockState(this.pos).is(BlockTags.FENCES);
    }

    // Paper start - Track if a knot was created
    public static LeashFenceKnotEntity getOrCreateKnot(Level level, BlockPos pos) {
        return getOrCreateKnot(level, pos, null);
    }
    public static LeashFenceKnotEntity getOrCreateKnot(Level level, BlockPos pos, org.apache.commons.lang3.mutable.@Nullable MutableBoolean created) {
        // Paper end - Track if a knot was created
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        for (LeashFenceKnotEntity leashFenceKnotEntity : level.getEntitiesOfClass(
            LeashFenceKnotEntity.class, new AABB(x - 1.0, y - 1.0, z - 1.0, x + 1.0, y + 1.0, z + 1.0)
        )) {
            if (leashFenceKnotEntity.getPos().equals(pos)) {
                return leashFenceKnotEntity;
            }
        }

        LeashFenceKnotEntity leashFenceKnotEntity1 = new LeashFenceKnotEntity(level, pos);
        if (level.addFreshEntity(leashFenceKnotEntity1) && created != null) { created.setTrue(); } // Paper - Track if a knot was created
        return leashFenceKnotEntity1;
    }

    public void playPlacementSound() {
        this.playSound(SoundEvents.LEAD_TIED, 1.0F, 1.0F);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, 0, this.getPos());
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTick) {
        return this.getPosition(partialTick).add(0.0, 0.2, 0.0);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.LEAD);
    }
}
