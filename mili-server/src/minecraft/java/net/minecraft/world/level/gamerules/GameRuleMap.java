package net.minecraft.world.level.gamerules;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jspecify.annotations.Nullable;

public final class GameRuleMap {
    public static final Codec<GameRuleMap> CODEC = Codec.<GameRule<?>, Object>dispatchedMap(BuiltInRegistries.GAME_RULE.byNameCodec(), GameRule::valueCodec)
        .xmap(GameRuleMap::ofTrusted, GameRuleMap::map);
    private final Reference2ObjectMap<GameRule<?>, Object> map;
    private final @Nullable Object[] idAccess; // Paper - array backed gamerule access - array storage

    GameRuleMap(Reference2ObjectMap<GameRule<?>, Object> map) {
        this.map = map;
        // Paper start - array backed gamerule access - array storage
        idAccess = new Object[GameRule.LAST_GAMERULE_INDEX];
        for (final Map.Entry<GameRule<?>, Object> entry : map.entrySet()) {
            idAccess[entry.getKey().gameRuleIndex] = entry.getValue();
        }
        // Paper end - array backed gamerule access - array storage
    }

    private static GameRuleMap ofTrusted(Map<GameRule<?>, Object> rules) {
        return new GameRuleMap(new Reference2ObjectOpenHashMap<>(rules));
    }

    public static GameRuleMap of() {
        return new GameRuleMap(new Reference2ObjectOpenHashMap<>());
    }

    public static GameRuleMap of(Stream<GameRule<?>> rules) {
        Reference2ObjectOpenHashMap<GameRule<?>, Object> map = new Reference2ObjectOpenHashMap<>();
        rules.forEach(gameRule -> map.put((GameRule<?>)gameRule, gameRule.defaultValue()));
        return new GameRuleMap(map);
    }

    public static GameRuleMap copyOf(GameRuleMap rules) {
        return new GameRuleMap(new Reference2ObjectOpenHashMap<>(rules.map));
    }

    public boolean has(GameRule<?> rule) {
        return this.idAccess[rule.gameRuleIndex] != null; // Paper - array backed gamerule access - the gamerule map does not allow null values, so this suffices for a contains check (see net.minecraft.world.level.gamerules.GameRuleMap.setGameRule's non-null checks)
    }

    public <T> @Nullable T get(GameRule<T> rule) {
        return (T) this.idAccess[rule.gameRuleIndex]; // Paper - array backed gamerule access
    }

    public <T> void set(GameRule<T> rule, T value) {
        this.map.put(rule, value);
        this.idAccess[rule.gameRuleIndex] = value; // Paper - array backed gamerule access - above map is kept in sync instead of fully removing the map, which needs more diff.
    }

    public <T> @Nullable T remove(GameRule<T> rule) {
        this.idAccess[rule.gameRuleIndex] = null; // Paper - array backed gamerule access
        return (T)this.map.remove(rule);
    }

    public Set<GameRule<?>> keySet() {
        return this.map.keySet();
    }

    public int size() {
        return this.map.size();
    }

    @Override
    public String toString() {
        return this.map.toString();
    }

    public GameRuleMap withOther(GameRuleMap rules) {
        GameRuleMap gameRuleMap = copyOf(this);
        gameRuleMap.setFromIf(rules, gameRule -> true);
        return gameRuleMap;
    }

    public void setFromIf(GameRuleMap rules, Predicate<GameRule<?>> predicate) {
        for (GameRule<?> gameRule : rules.keySet()) {
            if (predicate.test(gameRule)) {
                setGameRule(rules, gameRule, this);
            }
        }
    }

    private static <T> void setGameRule(GameRuleMap from, GameRule<T> rule, GameRuleMap to) {
        to.set(rule, Objects.requireNonNull(from.get(rule)));
    }

    private Reference2ObjectMap<GameRule<?>, Object> map() {
        return this.map;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (other != null && other.getClass() == this.getClass()) {
            GameRuleMap gameRuleMap = (GameRuleMap)other;
            return Objects.equals(this.map, gameRuleMap.map);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.map);
    }

    public static class Builder {
        final Reference2ObjectMap<GameRule<?>, Object> map = new Reference2ObjectOpenHashMap<>();

        public <T> GameRuleMap.Builder set(GameRule<T> rule, T value) {
            this.map.put(rule, value);
            return this;
        }

        public GameRuleMap build() {
            return new GameRuleMap(this.map);
        }
    }
}
