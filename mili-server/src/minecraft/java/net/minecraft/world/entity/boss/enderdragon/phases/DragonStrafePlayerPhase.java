package net.minecraft.world.entity.boss.enderdragon.phases;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class DragonStrafePlayerPhase extends AbstractDragonPhaseInstance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FIREBALL_CHARGE_AMOUNT = 5;
    private int fireballCharge;
    private @Nullable Path currentPath;
    private @Nullable Vec3 targetLocation;
    private @Nullable LivingEntity attackTarget;
    private boolean holdingPatternClockwise;

    public DragonStrafePlayerPhase(EnderDragon dragon) {
        super(dragon);
    }

    @Override
    public void doServerTick(ServerLevel level) {
        if (this.attackTarget == null) {
            LOGGER.warn("Skipping player strafe phase because no player was found");
            this.dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        } else {
            if (this.currentPath != null && this.currentPath.isDone()) {
                double x = this.attackTarget.getX();
                double z = this.attackTarget.getZ();
                double d = x - this.dragon.getX();
                double d1 = z - this.dragon.getZ();
                double squareRoot = Math.sqrt(d * d + d1 * d1);
                double min = Math.min(0.4F + squareRoot / 80.0 - 1.0, 10.0);
                this.targetLocation = new Vec3(x, this.attackTarget.getY() + min, z);
            }

            double x = this.targetLocation == null ? 0.0 : this.targetLocation.distanceToSqr(this.dragon.getX(), this.dragon.getY(), this.dragon.getZ());
            if (x < 100.0 || x > 22500.0) {
                this.findNewTarget();
            }

            double z = 64.0;
            if (this.attackTarget.distanceToSqr(this.dragon) < 4096.0) {
                if (this.dragon.hasLineOfSight(this.attackTarget)) {
                    this.fireballCharge++;
                    Vec3 vec3 = new Vec3(this.attackTarget.getX() - this.dragon.getX(), 0.0, this.attackTarget.getZ() - this.dragon.getZ()).normalize();
                    Vec3 vec31 = new Vec3(
                            Mth.sin(this.dragon.getYRot() * (float) (Math.PI / 180.0)), 0.0, -Mth.cos(this.dragon.getYRot() * (float) (Math.PI / 180.0))
                        )
                        .normalize();
                    float f = (float)vec31.dot(vec3);
                    float f1 = (float)(Math.acos(f) * 180.0F / (float)Math.PI);
                    f1 += 0.5F;
                    if (this.fireballCharge >= 5 && f1 >= 0.0F && f1 < 10.0F) {
                        double squareRoot = 1.0;
                        Vec3 viewVector = this.dragon.getViewVector(1.0F);
                        double d2 = this.dragon.head.getX() - viewVector.x * 1.0;
                        double d3 = this.dragon.head.getY(0.5) + 0.5;
                        double d4 = this.dragon.head.getZ() - viewVector.z * 1.0;
                        double d5 = this.attackTarget.getX() - d2;
                        double d6 = this.attackTarget.getY(0.5) - d3;
                        double d7 = this.attackTarget.getZ() - d4;
                        Vec3 vec32 = new Vec3(d5, d6, d7);
                        if (false && !this.dragon.isSilent()) { // Paper - EnderDragon Events; Fire after shoot fireball event
                            level.levelEvent(null, LevelEvent.SOUND_DRAGON_FIREBALL, this.dragon.blockPosition(), 0);
                        }

                        DragonFireball dragonFireball = new DragonFireball(level, this.dragon, vec32.normalize());
                        dragonFireball.snapTo(d2, d3, d4, 0.0F, 0.0F);
                        if (new com.destroystokyo.paper.event.entity.EnderDragonShootFireballEvent((org.bukkit.entity.EnderDragon) this.dragon.getBukkitEntity(), (org.bukkit.entity.DragonFireball) dragonFireball.getBukkitEntity()).callEvent()) { // Paper - EnderDragon Events
                            if (!this.dragon.isSilent()) level.levelEvent(null, LevelEvent.SOUND_DRAGON_FIREBALL, this.dragon.blockPosition(), 0); // Paper - EnderDragon Events; Fire after shoot fireball event
                        level.addFreshEntity(dragonFireball);
                        } else dragonFireball.discard(null); // Paper - EnderDragon Events
                        this.fireballCharge = 0;
                        if (this.currentPath != null) {
                            while (!this.currentPath.isDone()) {
                                this.currentPath.advance();
                            }
                        }

                        this.dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
                    }
                } else if (this.fireballCharge > 0) {
                    this.fireballCharge--;
                }
            } else if (this.fireballCharge > 0) {
                this.fireballCharge--;
            }
        }
    }

    private void findNewTarget() {
        if (this.currentPath == null || this.currentPath.isDone()) {
            int i = this.dragon.findClosestNode();
            int i1 = i;
            if (this.dragon.getRandom().nextInt(8) == 0) {
                this.holdingPatternClockwise = !this.holdingPatternClockwise;
                i1 = i + 6;
            }

            if (this.holdingPatternClockwise) {
                i1++;
            } else {
                i1--;
            }

            if (this.dragon.getDragonFight() != null && this.dragon.getDragonFight().getCrystalsAlive() > 0) {
                i1 %= 12;
                if (i1 < 0) {
                    i1 += 12;
                }
            } else {
                i1 -= 12;
                i1 &= 7;
                i1 += 12;
            }

            this.currentPath = this.dragon.findPath(i, i1, null);
            if (this.currentPath != null) {
                this.currentPath.advance();
            }
        }

        this.navigateToNextPathNode();
    }

    private void navigateToNextPathNode() {
        if (this.currentPath != null && !this.currentPath.isDone()) {
            Vec3i nextNodePos = this.currentPath.getNextNodePos();
            this.currentPath.advance();
            double d = nextNodePos.getX();
            double d1 = nextNodePos.getZ();

            double d2;
            do {
                d2 = nextNodePos.getY() + this.dragon.getRandom().nextFloat() * 20.0F;
            } while (d2 < nextNodePos.getY());

            this.targetLocation = new Vec3(d, d2, d1);
        }
    }

    @Override
    public void begin() {
        this.fireballCharge = 0;
        this.targetLocation = null;
        this.currentPath = null;
        this.attackTarget = null;
    }

    public void setTarget(LivingEntity attackTarget) {
        this.attackTarget = attackTarget;
        int i = this.dragon.findClosestNode();
        int i1 = this.dragon.findClosestNode(this.attackTarget.getX(), this.attackTarget.getY(), this.attackTarget.getZ());
        int blockX = this.attackTarget.getBlockX();
        int blockZ = this.attackTarget.getBlockZ();
        double d = blockX - this.dragon.getX();
        double d1 = blockZ - this.dragon.getZ();
        double squareRoot = Math.sqrt(d * d + d1 * d1);
        double min = Math.min(0.4F + squareRoot / 80.0 - 1.0, 10.0);
        int floor = Mth.floor(this.attackTarget.getY() + min);
        Node node = new Node(blockX, floor, blockZ);
        this.currentPath = this.dragon.findPath(i, i1, node);
        if (this.currentPath != null) {
            this.currentPath.advance();
            this.navigateToNextPathNode();
        }
    }

    @Override
    public @Nullable Vec3 getFlyTargetLocation() {
        return this.targetLocation;
    }

    @Override
    public EnderDragonPhase<DragonStrafePlayerPhase> getPhase() {
        return EnderDragonPhase.STRAFE_PLAYER;
    }
}
