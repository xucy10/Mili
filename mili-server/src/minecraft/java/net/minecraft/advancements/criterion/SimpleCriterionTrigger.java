package net.minecraft.advancements.criterion;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;

public abstract class SimpleCriterionTrigger<T extends SimpleCriterionTrigger.SimpleInstance> implements CriterionTrigger<T> {
    // private final Map<PlayerAdvancements, Set<CriterionTrigger.Listener<T>>> players = Maps.newIdentityHashMap(); // Paper - fix PlayerAdvancements leak; moved into PlayerAdvancements to fix memory leak

    @Override
    public final void addPlayerListener(PlayerAdvancements playerAdvancements, CriterionTrigger.Listener<T> listener) {
        playerAdvancements.criterionData.computeIfAbsent(this, managerx -> Sets.newHashSet()).add(listener); // Paper - fix PlayerAdvancements leak
    }

    @Override
    public final void removePlayerListener(PlayerAdvancements playerAdvancements, CriterionTrigger.Listener<T> listener) {
        Set<CriterionTrigger.Listener<T>> set = (Set) playerAdvancements.criterionData.get(this); // Paper - fix PlayerAdvancements leak
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty()) {
                playerAdvancements.criterionData.remove(this); // Paper - fix PlayerAdvancements leak
            }
        }
    }

    @Override
    public final void removePlayerListeners(PlayerAdvancements playerAdvancements) {
        playerAdvancements.criterionData.remove(this); // Paper - fix PlayerAdvancements leak
    }

    protected void trigger(ServerPlayer player, Predicate<T> testTrigger) {
        PlayerAdvancements advancements = player.getAdvancements();
        Set<CriterionTrigger.Listener<T>> set = (Set) advancements.criterionData.get(this); // Paper - fix PlayerAdvancements leak
        if (set != null && !set.isEmpty()) {
            LootContext lootContext = null; // EntityPredicate.createContext(player, player); // Paper - Perf: lazily create LootContext for criterions
            List<CriterionTrigger.Listener<T>> list = null;

            for (CriterionTrigger.Listener<T> listener : set) {
                T simpleInstance = listener.trigger();
                if (testTrigger.test(simpleInstance)) {
                    Optional<ContextAwarePredicate> optional = simpleInstance.player();
                    if (optional.isEmpty() || optional.get().matches(lootContext = (lootContext == null ? EntityPredicate.createContext(player, player) : lootContext))) { // Paper - Perf: lazily create LootContext for criterions
                        if (list == null) {
                            list = Lists.newArrayList();
                        }

                        list.add(listener);
                    }
                }
            }

            if (list != null) {
                for (CriterionTrigger.Listener<T> listenerx : list) {
                    listenerx.run(advancements);
                }
            }
        }
    }

    public interface SimpleInstance extends CriterionTriggerInstance {
        @Override
        default void validate(CriterionValidator validator) {
            validator.validateEntity(this.player(), "player");
        }

        Optional<ContextAwarePredicate> player();
    }
}
