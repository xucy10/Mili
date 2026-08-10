package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class PatrollingMonster extends Monster {
    private static final boolean DEFAULT_PATROL_LEADER = false;
    private static final boolean DEFAULT_PATROLLING = false;
    private @Nullable BlockPos patrolTarget;
    private boolean patrolLeader = false;
    private boolean patrolling = false;

    protected PatrollingMonster(EntityType<? extends PatrollingMonster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(4, new PatrollingMonster.LongDistancePatrolGoal<>(this, 0.7, 0.595));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("patrol_target", BlockPos.CODEC, this.patrolTarget);
        output.putBoolean("PatrolLeader", this.patrolLeader);
        output.putBoolean("Patrolling", this.patrolling);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.patrolTarget = input.read("patrol_target", BlockPos.CODEC).orElse(null);
        this.patrolLeader = input.getBooleanOr("PatrolLeader", false);
        this.patrolling = input.getBooleanOr("Patrolling", false);
    }

    public boolean canBeLeader() {
        return true;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        if (spawnReason != EntitySpawnReason.PATROL
            && spawnReason != EntitySpawnReason.EVENT
            && spawnReason != EntitySpawnReason.STRUCTURE
            && level.getRandom().nextFloat() < 0.06F
            && this.canBeLeader()) {
            this.patrolLeader = true;
        }

        if (this.isPatrolLeader()) {
            this.setItemSlot(EquipmentSlot.HEAD, Raid.getOminousBannerInstance(this.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
            this.setDropChance(EquipmentSlot.HEAD, 2.0F);
        }

        if (spawnReason == EntitySpawnReason.PATROL) {
            this.patrolling = true;
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    public static boolean checkPatrollingMonsterSpawnRules(
        EntityType<? extends PatrollingMonster> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource randomSource
    ) {
        return level.getBrightness(LightLayer.BLOCK, pos) <= 8 && checkAnyLightMonsterSpawnRules(entityType, level, spawnReason, pos, randomSource);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.patrolling || distanceToClosestPlayer > 16384.0;
    }

    public void setPatrolTarget(BlockPos patrolTarget) {
        this.patrolTarget = patrolTarget;
        this.patrolling = true;
    }

    public @Nullable BlockPos getPatrolTarget() {
        return this.patrolTarget;
    }

    public boolean hasPatrolTarget() {
        return this.patrolTarget != null;
    }

    public void setPatrolLeader(boolean patrolLeader) {
        this.patrolLeader = patrolLeader;
        this.patrolling = true;
    }

    public boolean isPatrolLeader() {
        return this.patrolLeader;
    }

    public boolean canJoinPatrol() {
        return true;
    }

    public void findPatrolTarget() {
        this.patrolTarget = this.blockPosition().offset(-500 + this.random.nextInt(1000), 0, -500 + this.random.nextInt(1000));
        this.patrolling = true;
    }

    protected boolean isPatrolling() {
        return this.patrolling;
    }

    protected void setPatrolling(boolean patrolling) {
        this.patrolling = patrolling;
    }

    public static class LongDistancePatrolGoal<T extends PatrollingMonster> extends Goal {
        private static final int NAVIGATION_FAILED_COOLDOWN = 200;
        private final T mob;
        private final double speedModifier;
        private final double leaderSpeedModifier;
        private long cooldownUntil;

        public LongDistancePatrolGoal(T mob, double speedModifier, double leaderSpeedModifier) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.leaderSpeedModifier = leaderSpeedModifier;
            this.cooldownUntil = -1L;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            boolean flag = this.mob.level().getGameTime() < this.cooldownUntil;
            return this.mob.isPatrolling() && this.mob.getTarget() == null && !this.mob.hasControllingPassenger() && this.mob.hasPatrolTarget() && !flag;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void tick() {
            boolean isPatrolLeader = this.mob.isPatrolLeader();
            PathNavigation navigation = this.mob.getNavigation();
            if (navigation.isDone()) {
                List<PatrollingMonster> list = this.findPatrolCompanions();
                if (this.mob.isPatrolling() && list.isEmpty()) {
                    this.mob.setPatrolling(false);
                } else if (isPatrolLeader && this.mob.getPatrolTarget().closerToCenterThan(this.mob.position(), 10.0)) {
                    this.mob.findPatrolTarget();
                } else {
                    Vec3 vec3 = Vec3.atBottomCenterOf(this.mob.getPatrolTarget());
                    Vec3 vec31 = this.mob.position();
                    Vec3 vec32 = vec31.subtract(vec3);
                    vec3 = vec32.yRot(90.0F).scale(0.4).add(vec3);
                    Vec3 vec33 = vec3.subtract(vec31).normalize().scale(10.0).add(vec31);
                    BlockPos blockPos = BlockPos.containing(vec33);
                    blockPos = this.mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockPos);
                    if (!navigation.moveTo(blockPos.getX(), blockPos.getY(), blockPos.getZ(), isPatrolLeader ? this.leaderSpeedModifier : this.speedModifier)) {
                        this.moveRandomly();
                        this.cooldownUntil = this.mob.level().getGameTime() + 200L;
                    } else if (isPatrolLeader) {
                        for (PatrollingMonster patrollingMonster : list) {
                            patrollingMonster.setPatrolTarget(blockPos);
                        }
                    }
                }
            }
        }

        private List<PatrollingMonster> findPatrolCompanions() {
            return this.mob
                .level()
                .getEntitiesOfClass(
                    PatrollingMonster.class,
                    this.mob.getBoundingBox().inflate(16.0),
                    patrollingMonster -> patrollingMonster.canJoinPatrol() && !patrollingMonster.is(this.mob)
                );
        }

        private boolean moveRandomly() {
            RandomSource random = this.mob.getRandom();
            BlockPos heightmapPos = this.mob
                .level()
                .getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.mob.blockPosition().offset(-8 + random.nextInt(16), 0, -8 + random.nextInt(16))
                );
            return this.mob.getNavigation().moveTo(heightmapPos.getX(), heightmapPos.getY(), heightmapPos.getZ(), this.speedModifier);
        }
    }
}
