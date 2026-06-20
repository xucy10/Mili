package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public class GameTestSequence {
    final GameTestInfo parent;
    private final List<GameTestEvent> events = Lists.newArrayList();
    private int lastTick;

    GameTestSequence(GameTestInfo parent) {
        this.parent = parent;
        this.lastTick = parent.getTick();
    }

    public GameTestSequence thenWaitUntil(Runnable task) {
        this.events.add(GameTestEvent.create(task));
        return this;
    }

    public GameTestSequence thenWaitUntil(long expectedDelay, Runnable task) {
        this.events.add(GameTestEvent.create(expectedDelay, task));
        return this;
    }

    public GameTestSequence thenIdle(int tick) {
        return this.thenExecuteAfter(tick, () -> {});
    }

    public GameTestSequence thenExecute(Runnable task) {
        this.events.add(GameTestEvent.create(() -> this.executeWithoutFail(task)));
        return this;
    }

    public GameTestSequence thenExecuteAfter(int tick, Runnable task) {
        this.events.add(GameTestEvent.create(() -> {
            if (this.parent.getTick() < this.lastTick + tick) {
                throw new GameTestAssertException(Component.translatable("test.error.sequence.not_completed"), this.parent.getTick());
            } else {
                this.executeWithoutFail(task);
            }
        }));
        return this;
    }

    public GameTestSequence thenExecuteFor(int tick, Runnable task) {
        this.events.add(GameTestEvent.create(() -> {
            if (this.parent.getTick() < this.lastTick + tick) {
                this.executeWithoutFail(task);
                throw new GameTestAssertException(Component.translatable("test.error.sequence.not_completed"), this.parent.getTick());
            }
        }));
        return this;
    }

    public void thenSucceed() {
        this.events.add(GameTestEvent.create(this.parent::succeed));
    }

    public void thenFail(Supplier<GameTestException> exception) {
        this.events.add(GameTestEvent.create(() -> this.parent.fail(exception.get())));
    }

    public GameTestSequence.Condition thenTrigger() {
        GameTestSequence.Condition condition = new GameTestSequence.Condition();
        this.events.add(GameTestEvent.create(() -> condition.trigger(this.parent.getTick())));
        return condition;
    }

    public void tickAndContinue(int tickCount) {
        try {
            this.tick(tickCount);
        } catch (GameTestAssertException var3) {
        }
    }

    public void tickAndFailIfNotComplete(int tickCount) {
        try {
            this.tick(tickCount);
        } catch (GameTestAssertException var3) {
            this.parent.fail(var3);
        }
    }

    private void executeWithoutFail(Runnable task) {
        try {
            task.run();
        } catch (GameTestAssertException var3) {
            this.parent.fail(var3);
        }
    }

    private void tick(int tickCount) {
        Iterator<GameTestEvent> iterator = this.events.iterator();

        while (iterator.hasNext()) {
            GameTestEvent gameTestEvent = iterator.next();
            gameTestEvent.assertion.run();
            iterator.remove();
            int i = tickCount - this.lastTick;
            int i1 = this.lastTick;
            this.lastTick = tickCount;
            if (gameTestEvent.expectedDelay != null && gameTestEvent.expectedDelay != i) {
                this.parent
                    .fail(new GameTestAssertException(Component.translatable("test.error.sequence.invalid_tick", i1 + gameTestEvent.expectedDelay), tickCount));
                break;
            }
        }
    }

    public class Condition {
        private static final int NOT_TRIGGERED = -1;
        private int triggerTime = -1;

        void trigger(int triggerTime) {
            if (this.triggerTime != -1) {
                throw new IllegalStateException("Condition already triggered at " + this.triggerTime);
            } else {
                this.triggerTime = triggerTime;
            }
        }

        public void assertTriggeredThisTick() {
            int tick = GameTestSequence.this.parent.getTick();
            if (this.triggerTime != tick) {
                if (this.triggerTime == -1) {
                    throw new GameTestAssertException(Component.translatable("test.error.sequence.condition_not_triggered"), tick);
                } else {
                    throw new GameTestAssertException(Component.translatable("test.error.sequence.condition_already_triggered", this.triggerTime), tick);
                }
            }
        }
    }
}
