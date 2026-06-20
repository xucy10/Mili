package net.minecraft.world.scores;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

class PlayerScores {
    private final Reference2ObjectOpenHashMap<Objective, Score> scores = new Reference2ObjectOpenHashMap<>(16, 0.5F);

    public @Nullable Score get(Objective objective) {
        return this.scores.get(objective);
    }

    public Score getOrCreate(Objective objective, Consumer<Score> creator) {
        return this.scores.computeIfAbsent(objective, object -> {
            Score score = new Score();
            creator.accept(score);
            return score;
        });
    }

    public boolean remove(Objective objective) {
        return this.scores.remove(objective) != null;
    }

    public boolean hasScores() {
        return !this.scores.isEmpty();
    }

    public Object2IntMap<Objective> listScores() {
        Object2IntMap<Objective> map = new Object2IntOpenHashMap<>();
        this.scores.forEach((objective, score) -> map.put(objective, score.value()));
        return map;
    }

    void setScore(Objective objective, Score score) {
        this.scores.put(objective, score);
    }

    Map<Objective, Score> listRawScores() {
        return Collections.unmodifiableMap(this.scores);
    }
}
