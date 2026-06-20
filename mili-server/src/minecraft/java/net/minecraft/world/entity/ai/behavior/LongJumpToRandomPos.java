package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LongJumpToRandomPos<E extends Mob> extends Behavior<E> {
    protected static final int FIND_JUMP_TRIES = 20;
    private static final int PREPARE_JUMP_DURATION = 40;
    protected static final int MIN_PATHFIND_DISTANCE_TO_VALID_JUMP = 8;
    private static final int TIME_OUT_DURATION = 200;
    private static final List<Integer> ALLOWED_ANGLES = Lists.newArrayList(65, 70, 75, 80);
    private final UniformInt timeBetweenLongJumps;
    protected final int maxLongJumpHeight;
    protected final int maxLongJumpWidth;
    protected final float maxJumpVelocityMultiplier;
    protected List<LongJumpToRandomPos.PossibleJump> jumpCandidates = Lists.newArrayList();
    protected Optional<Vec3> initialPosition = Optional.empty();
    protected @Nullable Vec3 chosenJump;
    protected int findJumpTries;
    protected long prepareJumpStart;
    private final Function<E, SoundEvent> getJumpSound;
    private final BiPredicate<E, BlockPos> acceptableLandingSpot;

    public LongJumpToRandomPos(
        UniformInt timeBetweenLongJumps, int maxLongJumpHeight, int maxLongJumpWidth, float maxJumpVelocity, Function<E, SoundEvent> getJumpSound
    ) {
        this(timeBetweenLongJumps, maxLongJumpHeight, maxLongJumpWidth, maxJumpVelocity, getJumpSound, LongJumpToRandomPos::defaultAcceptableLandingSpot);
    }

    public static <E extends Mob> boolean defaultAcceptableLandingSpot(E mob, BlockPos pos) {
        Level level = mob.level();
        BlockPos blockPos = pos.below();
        return level.getBlockState(blockPos).isSolidRender() && mob.getPathfindingMalus(WalkNodeEvaluator.getPathTypeStatic(mob, pos)) == 0.0F;
    }

    public LongJumpToRandomPos(
        UniformInt timeBetweenLongJumps,
        int maxLongJumpHeight,
        int maxLongJumpWidth,
        float maxJumpVelocityMultiplier,
        Function<E, SoundEvent> getJumpSound,
        BiPredicate<E, BlockPos> acceptableLandingSpot
    ) {
        super(
            ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LONG_JUMP_MID_JUMP,
                MemoryStatus.VALUE_ABSENT
            ),
            200
        );
        this.timeBetweenLongJumps = timeBetweenLongJumps;
        this.maxLongJumpHeight = maxLongJumpHeight;
        this.maxLongJumpWidth = maxLongJumpWidth;
        this.maxJumpVelocityMultiplier = maxJumpVelocityMultiplier;
        this.getJumpSound = getJumpSound;
        this.acceptableLandingSpot = acceptableLandingSpot;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Mob owner) {
        boolean flag = owner.onGround() && !owner.isInWater() && !owner.isInLava() && !level.getBlockState(owner.blockPosition()).is(Blocks.HONEY_BLOCK);
        if (!flag) {
            owner.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, this.timeBetweenLongJumps.sample(level.random) / 2);
        }

        return flag;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Mob entity, long gameTime) {
        boolean flag = this.initialPosition.isPresent()
            && this.initialPosition.get().equals(entity.position())
            && this.findJumpTries > 0
            && !entity.isInWater()
            && (this.chosenJump != null || !this.jumpCandidates.isEmpty());
        if (!flag && entity.getBrain().getMemory(MemoryModuleType.LONG_JUMP_MID_JUMP).isEmpty()) {
            entity.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, this.timeBetweenLongJumps.sample(level.random) / 2);
            entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }

        return flag;
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        this.chosenJump = null;
        this.findJumpTries = 20;
        this.initialPosition = Optional.of(entity.position());
        BlockPos blockPos = entity.blockPosition();
        int x = blockPos.getX();
        int y = blockPos.getY();
        int z = blockPos.getZ();
        this.jumpCandidates = BlockPos.betweenClosedStream(
                x - this.maxLongJumpWidth,
                y - this.maxLongJumpHeight,
                z - this.maxLongJumpWidth,
                x + this.maxLongJumpWidth,
                y + this.maxLongJumpHeight,
                z + this.maxLongJumpWidth
            )
            .filter(blockPos1 -> !blockPos1.equals(blockPos))
            .map(blockPos1 -> new LongJumpToRandomPos.PossibleJump(blockPos1.immutable(), Mth.ceil(blockPos.distSqr(blockPos1))))
            .collect(Collectors.toCollection(Lists::newArrayList));
    }

    @Override
    protected void tick(ServerLevel level, E owner, long gameTime) {
        if (this.chosenJump != null) {
            if (gameTime - this.prepareJumpStart >= 40L) {
                owner.setYRot(owner.yBodyRot);
                owner.setDiscardFriction(true);
                double len = this.chosenJump.length();
                double d = len + owner.getJumpBoostPower();
                owner.setDeltaMovement(this.chosenJump.scale(d / len));
                owner.getBrain().setMemory(MemoryModuleType.LONG_JUMP_MID_JUMP, true);
                level.playSound(null, owner, this.getJumpSound.apply(owner), SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        } else {
            this.findJumpTries--;
            this.pickCandidate(level, owner, gameTime);
        }
    }

    protected void pickCandidate(ServerLevel level, E entity, long prepareJumpStart) {
        while (!this.jumpCandidates.isEmpty()) {
            Optional<LongJumpToRandomPos.PossibleJump> jumpCandidate = this.getJumpCandidate(level);
            if (!jumpCandidate.isEmpty()) {
                LongJumpToRandomPos.PossibleJump possibleJump = jumpCandidate.get();
                BlockPos blockPos = possibleJump.targetPos();
                if (this.isAcceptableLandingPosition(level, entity, blockPos)) {
                    Vec3 vec3 = Vec3.atCenterOf(blockPos);
                    Vec3 vec31 = this.calculateOptimalJumpVector(entity, vec3);
                    if (vec31 != null) {
                        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(blockPos));
                        PathNavigation navigation = entity.getNavigation();
                        Path path = navigation.createPath(blockPos, 0, 8);
                        if (path == null || !path.canReach()) {
                            this.chosenJump = vec31;
                            this.prepareJumpStart = prepareJumpStart;
                            return;
                        }
                    }
                }
            }
        }
    }

    protected Optional<LongJumpToRandomPos.PossibleJump> getJumpCandidate(ServerLevel level) {
        Optional<LongJumpToRandomPos.PossibleJump> randomItem = WeightedRandom.getRandomItem(
            level.random, this.jumpCandidates, LongJumpToRandomPos.PossibleJump::weight
        );
        randomItem.ifPresent(this.jumpCandidates::remove);
        return randomItem;
    }

    private boolean isAcceptableLandingPosition(ServerLevel level, E entity, BlockPos pos) {
        BlockPos blockPos = entity.blockPosition();
        int x = blockPos.getX();
        int z = blockPos.getZ();
        return (x != pos.getX() || z != pos.getZ()) && this.acceptableLandingSpot.test(entity, pos);
    }

    protected @Nullable Vec3 calculateOptimalJumpVector(Mob mob, Vec3 target) {
        List<Integer> list = Lists.newArrayList(ALLOWED_ANGLES);
        Collections.shuffle(list);
        float f = (float)(mob.getAttributeValue(Attributes.JUMP_STRENGTH) * this.maxJumpVelocityMultiplier);

        for (int i : list) {
            Optional<Vec3> optional = LongJumpUtil.calculateJumpVectorForAngle(mob, target, f, i, true);
            if (optional.isPresent()) {
                return optional.get();
            }
        }

        return null;
    }

    public record PossibleJump(BlockPos targetPos, int weight) {
    }
}
