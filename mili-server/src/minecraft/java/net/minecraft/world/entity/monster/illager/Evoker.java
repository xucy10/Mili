package net.minecraft.world.entity.monster.illager;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

public class Evoker extends SpellcasterIllager {
    private @Nullable Sheep wololoTarget;

    public Evoker(EntityType<? extends Evoker> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new Evoker.EvokerCastingSpellGoal());
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 8.0F, 0.6, 1.0));
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Creaking.class, 8.0F, 0.6, 1.0));
        this.goalSelector.addGoal(4, new Evoker.EvokerSummonSpellGoal());
        this.goalSelector.addGoal(5, new Evoker.EvokerAttackSpellGoal());
        this.goalSelector.addGoal(6, new Evoker.EvokerWololoSpellGoal());
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true).setUnseenMemoryTicks(300));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false).setUnseenMemoryTicks(300));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, false));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 12.0).add(Attributes.MAX_HEALTH, 24.0);
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.EVOKER_CELEBRATE;
    }

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        return entity == this
            || super.considersEntityAsAlly(entity)
            || entity instanceof Vex vex && vex.getOwner() != null && this.considersEntityAsAlly(vex.getOwner());
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.EVOKER_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.EVOKER_DEATH;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.EVOKER_HURT;
    }

    public void setWololoTarget(@Nullable Sheep wololoTarget) {
        this.wololoTarget = wololoTarget;
    }

    @Nullable public Sheep getWololoTarget() {
        return this.wololoTarget;
    }

    @Override
    protected SoundEvent getCastingSoundEvent() {
        return SoundEvents.EVOKER_CAST_SPELL;
    }

    @Override
    public void applyRaidBuffs(ServerLevel level, int wave, boolean flag) {
    }

    class EvokerAttackSpellGoal extends SpellcasterIllager.SpellcasterUseSpellGoal {
        @Override
        protected int getCastingTime() {
            return 40;
        }

        @Override
        protected int getCastingInterval() {
            return 100;
        }

        @Override
        protected void performSpellCasting() {
            LivingEntity target = Evoker.this.getTarget();
            double min = Math.min(target.getY(), Evoker.this.getY());
            double d = Math.max(target.getY(), Evoker.this.getY()) + 1.0;
            float f = (float)Mth.atan2(target.getZ() - Evoker.this.getZ(), target.getX() - Evoker.this.getX());
            if (Evoker.this.distanceToSqr(target) < 9.0) {
                for (int i = 0; i < 5; i++) {
                    float f1 = f + i * (float) Math.PI * 0.4F;
                    this.createSpellEntity(Evoker.this.getX() + Mth.cos(f1) * 1.5, Evoker.this.getZ() + Mth.sin(f1) * 1.5, min, d, f1, 0);
                }

                for (int i = 0; i < 8; i++) {
                    float f1 = f + i * (float) Math.PI * 2.0F / 8.0F + (float) (Math.PI * 2.0 / 5.0);
                    this.createSpellEntity(Evoker.this.getX() + Mth.cos(f1) * 2.5, Evoker.this.getZ() + Mth.sin(f1) * 2.5, min, d, f1, 3);
                }
            } else {
                for (int i = 0; i < 16; i++) {
                    double d1 = 1.25 * (i + 1);
                    int i1 = 1 * i;
                    this.createSpellEntity(Evoker.this.getX() + Mth.cos(f) * d1, Evoker.this.getZ() + Mth.sin(f) * d1, min, d, f, i1);
                }
            }
        }

        private void createSpellEntity(double x, double z, double minY, double maxY, float yRot, int warmupDelay) {
            BlockPos blockPos = BlockPos.containing(x, maxY, z);
            boolean flag = false;
            double d = 0.0;

            do {
                BlockPos blockPos1 = blockPos.below();
                BlockState blockState = Evoker.this.level().getBlockState(blockPos1);
                if (blockState.isFaceSturdy(Evoker.this.level(), blockPos1, Direction.UP)) {
                    if (!Evoker.this.level().isEmptyBlock(blockPos)) {
                        BlockState blockState1 = Evoker.this.level().getBlockState(blockPos);
                        VoxelShape collisionShape = blockState1.getCollisionShape(Evoker.this.level(), blockPos);
                        if (!collisionShape.isEmpty()) {
                            d = collisionShape.max(Direction.Axis.Y);
                        }
                    }

                    flag = true;
                    break;
                }

                blockPos = blockPos.below();
            } while (blockPos.getY() >= Mth.floor(minY) - 1);

            if (flag) {
                Evoker.this.level().addFreshEntity(new EvokerFangs(Evoker.this.level(), x, blockPos.getY() + d, z, yRot, warmupDelay, Evoker.this));
                Evoker.this.level().gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x, blockPos.getY() + d, z), GameEvent.Context.of(Evoker.this));
            }
        }

        @Override
        protected SoundEvent getSpellPrepareSound() {
            return SoundEvents.EVOKER_PREPARE_ATTACK;
        }

        @Override
        protected SpellcasterIllager.IllagerSpell getSpell() {
            return SpellcasterIllager.IllagerSpell.FANGS;
        }
    }

    class EvokerCastingSpellGoal extends SpellcasterIllager.SpellcasterCastingSpellGoal {
        @Override
        public void tick() {
            if (Evoker.this.getTarget() != null) {
                Evoker.this.getLookControl().setLookAt(Evoker.this.getTarget(), Evoker.this.getMaxHeadYRot(), Evoker.this.getMaxHeadXRot());
            } else if (Evoker.this.getWololoTarget() != null) {
                Evoker.this.getLookControl().setLookAt(Evoker.this.getWololoTarget(), Evoker.this.getMaxHeadYRot(), Evoker.this.getMaxHeadXRot());
            }
        }
    }

    class EvokerSummonSpellGoal extends SpellcasterIllager.SpellcasterUseSpellGoal {
        private final TargetingConditions vexCountTargeting = TargetingConditions.forNonCombat().range(16.0).ignoreLineOfSight().ignoreInvisibilityTesting();

        @Override
        public boolean canUse() {
            if (!super.canUse()) {
                return false;
            } else {
                int size = getServerLevel(Evoker.this.level())
                    .getNearbyEntities(Vex.class, this.vexCountTargeting, Evoker.this, Evoker.this.getBoundingBox().inflate(16.0))
                    .size();
                return Evoker.this.random.nextInt(8) + 1 > size;
            }
        }

        @Override
        protected int getCastingTime() {
            return 100;
        }

        @Override
        protected int getCastingInterval() {
            return 340;
        }

        @Override
        protected void performSpellCasting() {
            ServerLevel serverLevel = (ServerLevel)Evoker.this.level();
            PlayerTeam team = Evoker.this.getTeam();

            for (int i = 0; i < 3; i++) {
                BlockPos blockPos = Evoker.this.blockPosition().offset(-2 + Evoker.this.random.nextInt(5), 1, -2 + Evoker.this.random.nextInt(5));
                Vex vex = EntityType.VEX.create(Evoker.this.level(), EntitySpawnReason.MOB_SUMMONED);
                if (vex != null) {
                    vex.snapTo(blockPos, 0.0F, 0.0F);
                    vex.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(blockPos), EntitySpawnReason.MOB_SUMMONED, null);
                    vex.setOwner(Evoker.this);
                    vex.setBoundOrigin(blockPos);
                    vex.setLimitedLife(20 * (30 + Evoker.this.random.nextInt(90)));
                    if (team != null) {
                        serverLevel.getScoreboard().addPlayerToTeam(vex.getScoreboardName(), team);
                    }

                    serverLevel.addFreshEntityWithPassengers(vex, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPELL); // CraftBukkit - Add SpawnReason
                    serverLevel.gameEvent(GameEvent.ENTITY_PLACE, blockPos, GameEvent.Context.of(Evoker.this));
                }
            }
        }

        @Override
        protected SoundEvent getSpellPrepareSound() {
            return SoundEvents.EVOKER_PREPARE_SUMMON;
        }

        @Override
        protected SpellcasterIllager.IllagerSpell getSpell() {
            return SpellcasterIllager.IllagerSpell.SUMMON_VEX;
        }
    }

    public class EvokerWololoSpellGoal extends SpellcasterIllager.SpellcasterUseSpellGoal {
        private final TargetingConditions wololoTargeting = TargetingConditions.forNonCombat()
            .range(16.0)
            .selector((entity, level) -> ((Sheep)entity).getColor() == DyeColor.BLUE);

        @Override
        public boolean canUse() {
            if (Evoker.this.getTarget() != null) {
                return false;
            } else if (Evoker.this.isCastingSpell()) {
                return false;
            } else if (Evoker.this.tickCount < this.nextAttackTickCount) {
                return false;
            } else {
                ServerLevel serverLevel = getServerLevel(Evoker.this.level());
                if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
                    return false;
                } else {
                    List<Sheep> nearbyEntities = serverLevel.getNearbyEntities(
                        Sheep.class, this.wololoTargeting, Evoker.this, Evoker.this.getBoundingBox().inflate(16.0, 4.0, 16.0)
                    );
                    if (nearbyEntities.isEmpty()) {
                        return false;
                    } else {
                        Evoker.this.setWololoTarget(nearbyEntities.get(Evoker.this.random.nextInt(nearbyEntities.size())));
                        return true;
                    }
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return Evoker.this.getWololoTarget() != null && this.attackWarmupDelay > 0;
        }

        @Override
        public void stop() {
            super.stop();
            Evoker.this.setWololoTarget(null);
        }

        @Override
        protected void performSpellCasting() {
            Sheep wololoTarget = Evoker.this.getWololoTarget();
            if (wololoTarget != null && wololoTarget.isAlive()) {
                wololoTarget.setColor(DyeColor.RED);
            }
        }

        @Override
        protected int getCastWarmupTime() {
            return 40;
        }

        @Override
        protected int getCastingTime() {
            return 60;
        }

        @Override
        protected int getCastingInterval() {
            return 140;
        }

        @Override
        protected SoundEvent getSpellPrepareSound() {
            return SoundEvents.EVOKER_PREPARE_WOLOLO;
        }

        @Override
        protected SpellcasterIllager.IllagerSpell getSpell() {
            return SpellcasterIllager.IllagerSpell.WOLOLO;
        }
    }
}
