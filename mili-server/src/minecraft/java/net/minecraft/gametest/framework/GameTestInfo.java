package net.minecraft.gametest.framework;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2LongMap.Entry;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class GameTestInfo {
    private final Holder.Reference<GameTestInstance> test;
    private @Nullable BlockPos testBlockPos;
    private final ServerLevel level;
    private final Collection<GameTestListener> listeners = Lists.newArrayList();
    private final int timeoutTicks;
    private final Collection<GameTestSequence> sequences = Lists.newCopyOnWriteArrayList();
    private final Object2LongMap<Runnable> runAtTickTimeMap = new Object2LongOpenHashMap<>();
    private boolean placedStructure;
    private boolean chunksLoaded;
    private int tickCount;
    private boolean started;
    private final RetryOptions retryOptions;
    private final Stopwatch timer = Stopwatch.createUnstarted();
    private boolean done;
    private final Rotation extraRotation;
    private @Nullable GameTestException error;
    private @Nullable TestInstanceBlockEntity testInstanceBlockEntity;

    public GameTestInfo(Holder.Reference<GameTestInstance> test, Rotation extraRotation, ServerLevel level, RetryOptions retryOptions) {
        this.test = test;
        this.level = level;
        this.retryOptions = retryOptions;
        this.timeoutTicks = test.value().maxTicks();
        this.extraRotation = extraRotation;
    }

    public void setTestBlockPos(@Nullable BlockPos testBlockPos) {
        this.testBlockPos = testBlockPos;
    }

    public GameTestInfo startExecution(int delay) {
        this.tickCount = -(this.test.value().setupTicks() + delay + 1);
        return this;
    }

    public void placeStructure() {
        if (!this.placedStructure) {
            TestInstanceBlockEntity testInstanceBlockEntity = this.getTestInstanceBlockEntity();
            if (!testInstanceBlockEntity.placeStructure()) {
                this.fail(Component.translatable("test.error.structure.failure", testInstanceBlockEntity.getTestName().getString()));
            }

            this.placedStructure = true;
            testInstanceBlockEntity.encaseStructure();
            BoundingBox structureBoundingBox = testInstanceBlockEntity.getStructureBoundingBox();
            this.level.getBlockTicks().clearArea(structureBoundingBox);
            this.level.clearBlockEvents(structureBoundingBox);
            this.listeners.forEach(listener -> listener.testStructureLoaded(this));
        }
    }

    public void tick(GameTestRunner runner) {
        if (!this.isDone()) {
            if (!this.placedStructure) {
                this.fail(Component.translatable("test.error.ticking_without_structure"));
            }

            if (this.testInstanceBlockEntity == null) {
                this.fail(Component.translatable("test.error.missing_block_entity"));
            }

            if (this.error != null) {
                this.finish();
            }

            if (this.chunksLoaded
                || this.testInstanceBlockEntity.getStructureBoundingBox().intersectingChunks().allMatch(this.level::areEntitiesActuallyLoadedAndTicking)) {
                this.chunksLoaded = true;
                this.tickInternal();
                if (this.isDone()) {
                    if (this.error != null) {
                        this.listeners.forEach(listener -> listener.testFailed(this, runner));
                    } else {
                        this.listeners.forEach(listener -> listener.testPassed(this, runner));
                    }
                }
            }
        }
    }

    private void tickInternal() {
        this.tickCount++;
        if (this.tickCount >= 0) {
            if (!this.started) {
                this.startTest();
            }

            ObjectIterator<Entry<Runnable>> objectIterator = this.runAtTickTimeMap.object2LongEntrySet().iterator();

            while (objectIterator.hasNext()) {
                Entry<Runnable> entry = objectIterator.next();
                if (entry.getLongValue() <= this.tickCount) {
                    try {
                        entry.getKey().run();
                    } catch (GameTestException var4) {
                        this.fail(var4);
                    } catch (Exception var5) {
                        this.fail(new UnknownGameTestException(var5));
                    }

                    objectIterator.remove();
                }
            }

            if (this.tickCount > this.timeoutTicks) {
                if (this.sequences.isEmpty()) {
                    this.fail(new GameTestTimeoutException(Component.translatable("test.error.timeout.no_result", this.test.value().maxTicks())));
                } else {
                    this.sequences.forEach(sequence -> sequence.tickAndFailIfNotComplete(this.tickCount));
                    if (this.error == null) {
                        this.fail(
                            new GameTestTimeoutException(Component.translatable("test.error.timeout.no_sequences_finished", this.test.value().maxTicks()))
                        );
                    }
                }
            } else {
                this.sequences.forEach(sequence -> sequence.tickAndContinue(this.tickCount));
            }
        }
    }

    private void startTest() {
        if (!this.started) {
            this.started = true;
            this.timer.start();
            this.getTestInstanceBlockEntity().setRunning();

            try {
                this.test.value().run(new GameTestHelper(this));
            } catch (GameTestException var2) {
                this.fail(var2);
            } catch (Exception var3) {
                this.fail(new UnknownGameTestException(var3));
            }
        }
    }

    public void setRunAtTickTime(long tickTime, Runnable task) {
        this.runAtTickTimeMap.put(task, tickTime);
    }

    public Identifier id() {
        return this.test.key().identifier();
    }

    public @Nullable BlockPos getTestBlockPos() {
        return this.testBlockPos;
    }

    public BlockPos getTestOrigin() {
        return this.testInstanceBlockEntity.getStartCorner();
    }

    public AABB getStructureBounds() {
        TestInstanceBlockEntity testInstanceBlockEntity = this.getTestInstanceBlockEntity();
        return testInstanceBlockEntity.getStructureBounds();
    }

    public TestInstanceBlockEntity getTestInstanceBlockEntity() {
        if (this.testInstanceBlockEntity == null) {
            if (this.testBlockPos == null) {
                throw new IllegalStateException("This GameTestInfo has no position");
            }

            if (this.level.getBlockEntity(this.testBlockPos) instanceof TestInstanceBlockEntity testInstanceBlockEntity) {
                this.testInstanceBlockEntity = testInstanceBlockEntity;
            }

            if (this.testInstanceBlockEntity == null) {
                throw new IllegalStateException("Could not find a test instance block entity at the given coordinate " + this.testBlockPos);
            }
        }

        return this.testInstanceBlockEntity;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public boolean hasSucceeded() {
        return this.done && this.error == null;
    }

    public boolean hasFailed() {
        return this.error != null;
    }

    public boolean hasStarted() {
        return this.started;
    }

    public boolean isDone() {
        return this.done;
    }

    public long getRunTime() {
        return this.timer.elapsed(TimeUnit.MILLISECONDS);
    }

    private void finish() {
        if (!this.done) {
            this.done = true;
            if (this.timer.isRunning()) {
                this.timer.stop();
            }
        }
    }

    public void succeed() {
        if (this.error == null) {
            this.finish();
            AABB structureBounds = this.getStructureBounds();
            List<Entity> entitiesOfClass = this.getLevel()
                .getEntitiesOfClass(Entity.class, structureBounds.inflate(1.0), entity -> !(entity instanceof Player));
            entitiesOfClass.forEach(entity -> entity.remove(Entity.RemovalReason.DISCARDED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD)); // Paper
        }
    }

    public void fail(Component message) {
        this.fail(new GameTestAssertException(message, this.tickCount));
    }

    public void fail(GameTestException error) {
        this.error = error;
    }

    public @Nullable GameTestException getError() {
        return this.error;
    }

    @Override
    public String toString() {
        return this.id().toString();
    }

    public void addListener(GameTestListener listener) {
        this.listeners.add(listener);
    }

    public @Nullable GameTestInfo prepareTestStructure() {
        TestInstanceBlockEntity testInstanceBlockEntity = this.createTestInstanceBlock(
            Objects.requireNonNull(this.testBlockPos), this.extraRotation, this.level
        );
        if (testInstanceBlockEntity != null) {
            this.testInstanceBlockEntity = testInstanceBlockEntity;
            this.placeStructure();
            return this;
        } else {
            return null;
        }
    }

    private @Nullable TestInstanceBlockEntity createTestInstanceBlock(BlockPos pos, Rotation rotation, ServerLevel level) {
        level.setBlockAndUpdate(pos, Blocks.TEST_INSTANCE_BLOCK.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof TestInstanceBlockEntity testInstanceBlockEntity) {
            ResourceKey<GameTestInstance> resourceKey = this.getTestHolder().key();
            Vec3i vec3i = TestInstanceBlockEntity.getStructureSize(level, resourceKey).orElse(new Vec3i(1, 1, 1));
            testInstanceBlockEntity.set(
                new TestInstanceBlockEntity.Data(Optional.of(resourceKey), vec3i, rotation, false, TestInstanceBlockEntity.Status.CLEARED, Optional.empty())
            );
            return testInstanceBlockEntity;
        } else {
            return null;
        }
    }

    int getTick() {
        return this.tickCount;
    }

    GameTestSequence createSequence() {
        GameTestSequence gameTestSequence = new GameTestSequence(this);
        this.sequences.add(gameTestSequence);
        return gameTestSequence;
    }

    public boolean isRequired() {
        return this.test.value().required();
    }

    public boolean isOptional() {
        return !this.test.value().required();
    }

    public Identifier getStructure() {
        return this.test.value().structure();
    }

    public Rotation getRotation() {
        return this.test.value().info().rotation().getRotated(this.extraRotation);
    }

    public GameTestInstance getTest() {
        return this.test.value();
    }

    public Holder.Reference<GameTestInstance> getTestHolder() {
        return this.test;
    }

    public int getTimeoutTicks() {
        return this.timeoutTicks;
    }

    public boolean isFlaky() {
        return this.test.value().maxAttempts() > 1;
    }

    public int maxAttempts() {
        return this.test.value().maxAttempts();
    }

    public int requiredSuccesses() {
        return this.test.value().requiredSuccesses();
    }

    public RetryOptions retryOptions() {
        return this.retryOptions;
    }

    public Stream<GameTestListener> getListeners() {
        return this.listeners.stream();
    }

    public GameTestInfo copyReset() {
        GameTestInfo gameTestInfo = new GameTestInfo(this.test, this.extraRotation, this.level, this.retryOptions());
        if (this.testBlockPos != null) {
            gameTestInfo.setTestBlockPos(this.testBlockPos);
        }

        return gameTestInfo;
    }
}
