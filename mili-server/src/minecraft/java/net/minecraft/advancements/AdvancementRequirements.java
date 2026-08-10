package net.minecraft.advancements;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.network.FriendlyByteBuf;

public record AdvancementRequirements(List<List<String>> requirements) {
    public static final Codec<AdvancementRequirements> CODEC = Codec.STRING
        .listOf()
        .listOf()
        .xmap(AdvancementRequirements::new, AdvancementRequirements::requirements);
    public static final AdvancementRequirements EMPTY = new AdvancementRequirements(List.of());

    public AdvancementRequirements(FriendlyByteBuf buffer) {
        this(buffer.readList(buffer1 -> buffer1.readList(FriendlyByteBuf::readUtf)));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeCollection(this.requirements, (buffer1, requirements) -> buffer1.writeCollection(requirements, FriendlyByteBuf::writeUtf));
    }

    public static AdvancementRequirements allOf(Collection<String> requirements) {
        return new AdvancementRequirements(requirements.stream().map(List::of).toList());
    }

    public static AdvancementRequirements anyOf(Collection<String> criteria) {
        return new AdvancementRequirements(List.of(List.copyOf(criteria)));
    }

    public int size() {
        return this.requirements.size();
    }

    public boolean test(Predicate<String> predicate) {
        if (this.requirements.isEmpty()) {
            return false;
        } else {
            for (List<String> list : this.requirements) {
                if (!anyMatch(list, predicate)) {
                    return false;
                }
            }

            return true;
        }
    }

    public int count(Predicate<String> filter) {
        int i = 0;

        for (List<String> list : this.requirements) {
            if (anyMatch(list, filter)) {
                i++;
            }
        }

        return i;
    }

    private static boolean anyMatch(List<String> requirements, Predicate<String> predicate) {
        for (String string : requirements) {
            if (predicate.test(string)) {
                return true;
            }
        }

        return false;
    }

    public DataResult<AdvancementRequirements> validate(Set<String> requirements) {
        Set<String> set = new ObjectOpenHashSet<>();

        for (List<String> list : this.requirements) {
            if (list.isEmpty() && requirements.isEmpty()) {
                return DataResult.error(() -> "Requirement entry cannot be empty");
            }

            set.addAll(list);
        }

        if (!requirements.equals(set)) {
            Set<String> set1 = Sets.difference(requirements, set);
            Set<String> set2 = Sets.difference(set, requirements);
            return DataResult.error(
                () -> "Advancement completion requirements did not exactly match specified criteria. Missing: " + set1 + ". Unknown: " + set2
            );
        } else {
            return DataResult.success(this);
        }
    }

    public boolean isEmpty() {
        return this.requirements.isEmpty();
    }

    @Override
    public String toString() {
        return this.requirements.toString();
    }

    public Set<String> names() {
        Set<String> set = new ObjectOpenHashSet<>();

        for (List<String> list : this.requirements) {
            set.addAll(list);
        }

        return set;
    }

    public interface Strategy {
        AdvancementRequirements.Strategy AND = AdvancementRequirements::allOf;
        AdvancementRequirements.Strategy OR = AdvancementRequirements::anyOf;

        AdvancementRequirements create(Collection<String> criteria);
    }
}
