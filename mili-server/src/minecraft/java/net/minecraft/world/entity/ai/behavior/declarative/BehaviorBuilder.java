package net.minecraft.world.entity.ai.behavior.declarative;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.datafixers.kinds.Const;
import com.mojang.datafixers.kinds.IdF;
import com.mojang.datafixers.kinds.K1;
import com.mojang.datafixers.kinds.OptionalBox;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Unit;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.jspecify.annotations.Nullable;

public class BehaviorBuilder<E extends LivingEntity, M> implements App<BehaviorBuilder.Mu<E>, M> {
    private final BehaviorBuilder.TriggerWithResult<E, M> trigger;

    public static <E extends LivingEntity, M> BehaviorBuilder<E, M> unbox(App<BehaviorBuilder.Mu<E>, M> app) {
        return (BehaviorBuilder<E, M>)app;
    }

    public static <E extends LivingEntity> BehaviorBuilder.Instance<E> instance() {
        return new BehaviorBuilder.Instance<>();
    }

    public static <E extends LivingEntity> OneShot<E> create(
        Function<BehaviorBuilder.Instance<E>, ? extends App<BehaviorBuilder.Mu<E>, Trigger<E>>> initializer
    ) {
        final BehaviorBuilder.TriggerWithResult<E, Trigger<E>> triggerWithResult = get((App<BehaviorBuilder.Mu<E>, Trigger<E>>)initializer.apply(instance()));
        return new OneShot<E>() {
            @Override
            public boolean trigger(ServerLevel level, E entity, long gameTime) {
                Trigger<E> trigger = triggerWithResult.tryTrigger(level, entity, gameTime);
                return trigger != null && trigger.trigger(level, entity, gameTime);
            }

            @Override
            public String debugString() {
                return "OneShot[" + triggerWithResult.debugString() + "]";
            }

            @Override
            public String toString() {
                return this.debugString();
            }
        };
    }

    public static <E extends LivingEntity> OneShot<E> sequence(Trigger<? super E> predicateTrigger, Trigger<? super E> trigger) {
        return create(instance -> instance.group(instance.ifTriggered(predicateTrigger)).apply(instance, unit -> trigger::trigger));
    }

    public static <E extends LivingEntity> OneShot<E> triggerIf(Predicate<E> predicate, OneShot<? super E> trigger) {
        return sequence(triggerIf(predicate), trigger);
    }

    public static <E extends LivingEntity> OneShot<E> triggerIf(Predicate<E> predicate) {
        return create(instance -> instance.point((level, entity, gameTime) -> predicate.test(entity)));
    }

    public static <E extends LivingEntity> OneShot<E> triggerIf(BiPredicate<ServerLevel, E> predicate) {
        return create(instance -> instance.point((level, entity, gameTime) -> predicate.test(level, entity)));
    }

    static <E extends LivingEntity, M> BehaviorBuilder.TriggerWithResult<E, M> get(App<BehaviorBuilder.Mu<E>, M> app) {
        return unbox(app).trigger;
    }

    BehaviorBuilder(BehaviorBuilder.TriggerWithResult<E, M> trigger) {
        this.trigger = trigger;
    }

    static <E extends LivingEntity, M> BehaviorBuilder<E, M> create(BehaviorBuilder.TriggerWithResult<E, M> trigger) {
        return new BehaviorBuilder<>(trigger);
    }

    static final class Constant<E extends LivingEntity, A> extends BehaviorBuilder<E, A> {
        Constant(A value) {
            this(value, () -> "C[" + value + "]");
        }

        Constant(final A value, final Supplier<String> name) {
            super(new BehaviorBuilder.TriggerWithResult<E, A>() {
                @Override
                public A tryTrigger(ServerLevel level, E entity, long gameTime) {
                    return value;
                }

                @Override
                public String debugString() {
                    return name.get();
                }

                @Override
                public String toString() {
                    return this.debugString();
                }
            });
        }
    }

    public static final class Instance<E extends LivingEntity> implements Applicative<BehaviorBuilder.Mu<E>, BehaviorBuilder.Instance.Mu<E>> {
        public <Value> Optional<Value> tryGet(MemoryAccessor<OptionalBox.Mu, Value> memory) {
            return OptionalBox.unbox(memory.value());
        }

        public <Value> Value get(MemoryAccessor<IdF.Mu, Value> memory) {
            return IdF.get(memory.value());
        }

        public <Value> BehaviorBuilder<E, MemoryAccessor<OptionalBox.Mu, Value>> registered(MemoryModuleType<Value> memoryType) {
            return new BehaviorBuilder.PureMemory<>(new MemoryCondition.Registered<>(memoryType));
        }

        public <Value> BehaviorBuilder<E, MemoryAccessor<IdF.Mu, Value>> present(MemoryModuleType<Value> memoryType) {
            return new BehaviorBuilder.PureMemory<>(new MemoryCondition.Present<>(memoryType));
        }

        public <Value> BehaviorBuilder<E, MemoryAccessor<Const.Mu<Unit>, Value>> absent(MemoryModuleType<Value> memoryType) {
            return new BehaviorBuilder.PureMemory<>(new MemoryCondition.Absent<>(memoryType));
        }

        public BehaviorBuilder<E, Unit> ifTriggered(Trigger<? super E> trigger) {
            return new BehaviorBuilder.TriggerWrapper<>(trigger);
        }

        @Override
        public <A> BehaviorBuilder<E, A> point(A value) {
            return new BehaviorBuilder.Constant<>(value);
        }

        public <A> BehaviorBuilder<E, A> point(Supplier<String> name, A value) {
            return new BehaviorBuilder.Constant<>(value, name);
        }

        @Override
        public <A, R> Function<App<BehaviorBuilder.Mu<E>, A>, App<BehaviorBuilder.Mu<E>, R>> lift1(App<BehaviorBuilder.Mu<E>, Function<A, R>> behavior) {
            return app -> {
                final BehaviorBuilder.TriggerWithResult<E, A> triggerWithResult = BehaviorBuilder.get(app);
                final BehaviorBuilder.TriggerWithResult<E, Function<A, R>> triggerWithResult1 = BehaviorBuilder.get(behavior);
                return BehaviorBuilder.create(new BehaviorBuilder.TriggerWithResult<E, R>() {
                    @Override
                    public R tryTrigger(ServerLevel level, E entity, long gameTime) {
                        A object = (A)triggerWithResult.tryTrigger(level, entity, gameTime);
                        if (object == null) {
                            return null;
                        } else {
                            Function<A, R> function = (Function<A, R>)triggerWithResult1.tryTrigger(level, entity, gameTime);
                            return (R)(function == null ? null : function.apply(object));
                        }
                    }

                    @Override
                    public String debugString() {
                        return triggerWithResult1.debugString() + " * " + triggerWithResult.debugString();
                    }

                    @Override
                    public String toString() {
                        return this.debugString();
                    }
                });
            };
        }

        @Override
        public <T, R> BehaviorBuilder<E, R> map(final Function<? super T, ? extends R> mapper, App<BehaviorBuilder.Mu<E>, T> behavior) {
            final BehaviorBuilder.TriggerWithResult<E, T> triggerWithResult = BehaviorBuilder.get(behavior);
            return BehaviorBuilder.create(new BehaviorBuilder.TriggerWithResult<E, R>() {
                @Override
                public R tryTrigger(ServerLevel level, E entity, long gameTime) {
                    T object = triggerWithResult.tryTrigger(level, entity, gameTime);
                    return (R)(object == null ? null : mapper.apply(object));
                }

                @Override
                public String debugString() {
                    return triggerWithResult.debugString() + ".map[" + mapper + "]";
                }

                @Override
                public String toString() {
                    return this.debugString();
                }
            });
        }

        @Override
        public <A, B, R> BehaviorBuilder<E, R> ap2(
            App<BehaviorBuilder.Mu<E>, BiFunction<A, B, R>> mapper, App<BehaviorBuilder.Mu<E>, A> behavior1, App<BehaviorBuilder.Mu<E>, B> behavior2
        ) {
            final BehaviorBuilder.TriggerWithResult<E, A> triggerWithResult = BehaviorBuilder.get(behavior1);
            final BehaviorBuilder.TriggerWithResult<E, B> triggerWithResult1 = BehaviorBuilder.get(behavior2);
            final BehaviorBuilder.TriggerWithResult<E, BiFunction<A, B, R>> triggerWithResult2 = BehaviorBuilder.get(mapper);
            return BehaviorBuilder.create(new BehaviorBuilder.TriggerWithResult<E, R>() {
                @Override
                public R tryTrigger(ServerLevel level, E entity, long gameTime) {
                    A object = triggerWithResult.tryTrigger(level, entity, gameTime);
                    if (object == null) {
                        return null;
                    } else {
                        B object1 = triggerWithResult1.tryTrigger(level, entity, gameTime);
                        if (object1 == null) {
                            return null;
                        } else {
                            BiFunction<A, B, R> biFunction = triggerWithResult2.tryTrigger(level, entity, gameTime);
                            return biFunction == null ? null : biFunction.apply(object, object1);
                        }
                    }
                }

                @Override
                public String debugString() {
                    return triggerWithResult2.debugString() + " * " + triggerWithResult.debugString() + " * " + triggerWithResult1.debugString();
                }

                @Override
                public String toString() {
                    return this.debugString();
                }
            });
        }

        @Override
        public <T1, T2, T3, R> BehaviorBuilder<E, R> ap3(
            App<BehaviorBuilder.Mu<E>, Function3<T1, T2, T3, R>> mapper,
            App<BehaviorBuilder.Mu<E>, T1> behavior1,
            App<BehaviorBuilder.Mu<E>, T2> behavior2,
            App<BehaviorBuilder.Mu<E>, T3> behavior3
        ) {
            final BehaviorBuilder.TriggerWithResult<E, T1> triggerWithResult = BehaviorBuilder.get(behavior1);
            final BehaviorBuilder.TriggerWithResult<E, T2> triggerWithResult1 = BehaviorBuilder.get(behavior2);
            final BehaviorBuilder.TriggerWithResult<E, T3> triggerWithResult2 = BehaviorBuilder.get(behavior3);
            final BehaviorBuilder.TriggerWithResult<E, Function3<T1, T2, T3, R>> triggerWithResult3 = BehaviorBuilder.get(mapper);
            return BehaviorBuilder.create(
                new BehaviorBuilder.TriggerWithResult<E, R>() {
                    @Override
                    public R tryTrigger(ServerLevel level, E entity, long gameTime) {
                        T1 object = triggerWithResult.tryTrigger(level, entity, gameTime);
                        if (object == null) {
                            return null;
                        } else {
                            T2 object1 = triggerWithResult1.tryTrigger(level, entity, gameTime);
                            if (object1 == null) {
                                return null;
                            } else {
                                T3 object2 = triggerWithResult2.tryTrigger(level, entity, gameTime);
                                if (object2 == null) {
                                    return null;
                                } else {
                                    Function3<T1, T2, T3, R> function3 = triggerWithResult3.tryTrigger(level, entity, gameTime);
                                    return function3 == null ? null : function3.apply(object, object1, object2);
                                }
                            }
                        }
                    }

                    @Override
                    public String debugString() {
                        return triggerWithResult3.debugString()
                            + " * "
                            + triggerWithResult.debugString()
                            + " * "
                            + triggerWithResult1.debugString()
                            + " * "
                            + triggerWithResult2.debugString();
                    }

                    @Override
                    public String toString() {
                        return this.debugString();
                    }
                }
            );
        }

        @Override
        public <T1, T2, T3, T4, R> BehaviorBuilder<E, R> ap4(
            App<BehaviorBuilder.Mu<E>, Function4<T1, T2, T3, T4, R>> mapper,
            App<BehaviorBuilder.Mu<E>, T1> behavior1,
            App<BehaviorBuilder.Mu<E>, T2> behavior2,
            App<BehaviorBuilder.Mu<E>, T3> behavior3,
            App<BehaviorBuilder.Mu<E>, T4> behavior4
        ) {
            final BehaviorBuilder.TriggerWithResult<E, T1> triggerWithResult = BehaviorBuilder.get(behavior1);
            final BehaviorBuilder.TriggerWithResult<E, T2> triggerWithResult1 = BehaviorBuilder.get(behavior2);
            final BehaviorBuilder.TriggerWithResult<E, T3> triggerWithResult2 = BehaviorBuilder.get(behavior3);
            final BehaviorBuilder.TriggerWithResult<E, T4> triggerWithResult3 = BehaviorBuilder.get(behavior4);
            final BehaviorBuilder.TriggerWithResult<E, Function4<T1, T2, T3, T4, R>> triggerWithResult4 = BehaviorBuilder.get(mapper);
            return BehaviorBuilder.create(
                new BehaviorBuilder.TriggerWithResult<E, R>() {
                    @Override
                    public R tryTrigger(ServerLevel level, E entity, long gameTime) {
                        T1 object = triggerWithResult.tryTrigger(level, entity, gameTime);
                        if (object == null) {
                            return null;
                        } else {
                            T2 object1 = triggerWithResult1.tryTrigger(level, entity, gameTime);
                            if (object1 == null) {
                                return null;
                            } else {
                                T3 object2 = triggerWithResult2.tryTrigger(level, entity, gameTime);
                                if (object2 == null) {
                                    return null;
                                } else {
                                    T4 object3 = triggerWithResult3.tryTrigger(level, entity, gameTime);
                                    if (object3 == null) {
                                        return null;
                                    } else {
                                        Function4<T1, T2, T3, T4, R> function4 = triggerWithResult4.tryTrigger(level, entity, gameTime);
                                        return function4 == null ? null : function4.apply(object, object1, object2, object3);
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public String debugString() {
                        return triggerWithResult4.debugString()
                            + " * "
                            + triggerWithResult.debugString()
                            + " * "
                            + triggerWithResult1.debugString()
                            + " * "
                            + triggerWithResult2.debugString()
                            + " * "
                            + triggerWithResult3.debugString();
                    }

                    @Override
                    public String toString() {
                        return this.debugString();
                    }
                }
            );
        }

        static final class Mu<E extends LivingEntity> implements Applicative.Mu {
            private Mu() {
            }
        }
    }

    public static final class Mu<E extends LivingEntity> implements K1 {
    }

    static final class PureMemory<E extends LivingEntity, F extends K1, Value> extends BehaviorBuilder<E, MemoryAccessor<F, Value>> {
        PureMemory(final MemoryCondition<F, Value> trigger) {
            super(new BehaviorBuilder.TriggerWithResult<E, MemoryAccessor<F, Value>>() {
                @Override
                public @Nullable MemoryAccessor<F, Value> tryTrigger(ServerLevel level, E entity, long gameTime) {
                    Brain<?> brain = entity.getBrain();
                    Optional<Value> memoryInternal = brain.getMemoryInternal(trigger.memory());
                    return memoryInternal == null ? null : trigger.createAccessor(brain, memoryInternal);
                }

                @Override
                public String debugString() {
                    return "M[" + trigger + "]";
                }

                @Override
                public String toString() {
                    return this.debugString();
                }
            });
        }
    }

    interface TriggerWithResult<E extends LivingEntity, R> {
        @Nullable R tryTrigger(ServerLevel level, E entity, long gameTime);

        String debugString();
    }

    static final class TriggerWrapper<E extends LivingEntity> extends BehaviorBuilder<E, Unit> {
        TriggerWrapper(final Trigger<? super E> trigger) {
            super(new BehaviorBuilder.TriggerWithResult<E, Unit>() {
                @Override
                public @Nullable Unit tryTrigger(ServerLevel level, E entity, long gameTime) {
                    return trigger.trigger(level, entity, gameTime) ? Unit.INSTANCE : null;
                }

                @Override
                public String debugString() {
                    return "T[" + trigger + "]";
                }
            });
        }
    }
}
