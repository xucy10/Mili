package net.minecraft.world.level.gamerules;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import org.jspecify.annotations.Nullable;

public class GameRules {
    // Paper start - allow disabling gamerule limits
    private static final boolean DISABLE_LIMITS = Boolean.getBoolean("paper.disableGameRuleLimits");

    private static int limit(final int limit, final int unlimited) {
        return DISABLE_LIMITS ? unlimited : limit;
    }
    // Paper end - allow disabling gamerule limits

    public static final GameRule<Boolean> ADVANCE_TIME = registerBoolean("advance_time", GameRuleCategory.UPDATES, !SharedConstants.DEBUG_WORLD_RECREATE);
    public static final GameRule<Boolean> ADVANCE_WEATHER = registerBoolean("advance_weather", GameRuleCategory.UPDATES, !SharedConstants.DEBUG_WORLD_RECREATE);
    public static final GameRule<Boolean> ALLOW_ENTERING_NETHER_USING_PORTALS = registerBoolean(
        "allow_entering_nether_using_portals", GameRuleCategory.MISC, true
    );
    public static final GameRule<Boolean> BLOCK_DROPS = registerBoolean("block_drops", GameRuleCategory.DROPS, true);
    public static final GameRule<Boolean> BLOCK_EXPLOSION_DROP_DECAY = registerBoolean("block_explosion_drop_decay", GameRuleCategory.DROPS, true);
    public static final GameRule<Boolean> COMMAND_BLOCKS_WORK = registerBoolean("command_blocks_work", GameRuleCategory.MISC, true);
    public static final GameRule<Boolean> COMMAND_BLOCK_OUTPUT = registerBoolean("command_block_output", GameRuleCategory.CHAT, true);
    public static final GameRule<Boolean> DROWNING_DAMAGE = registerBoolean("drowning_damage", GameRuleCategory.PLAYER, true);
    public static final GameRule<Boolean> ELYTRA_MOVEMENT_CHECK = registerBoolean("elytra_movement_check", GameRuleCategory.PLAYER, true);
    public static final GameRule<Boolean> ENDER_PEARLS_VANISH_ON_DEATH = registerBoolean("ender_pearls_vanish_on_death", GameRuleCategory.PLAYER, true);
    public static final GameRule<Boolean> ENTITY_DROPS = registerBoolean("entity_drops", GameRuleCategory.DROPS, true);
    public static final GameRule<Boolean> FALL_DAMAGE = registerBoolean("fall_damage", GameRuleCategory.PLAYER, true);
    public static final GameRule<Boolean> FIRE_DAMAGE = registerBoolean("fire_damage", GameRuleCategory.PLAYER, true);
    public static final GameRule<Integer> FIRE_SPREAD_RADIUS_AROUND_PLAYER = registerInteger(
        "fire_spread_radius_around_player", GameRuleCategory.UPDATES, 128, -1
    );
    public static final GameRule<Boolean> FORGIVE_DEAD_PLAYERS = registerBoolean("forgive_dead_players", GameRuleCategory.MOBS, true);
    public static final GameRule<Boolean> FREEZE_DAMAGE = registerBoolean("freeze_damage", GameRuleCategory.PLAYER, true);
    public static final GameRule<Boolean> GLOBAL_SOUND_EVENTS = registerBoolean("global_sound_events", GameRuleCategory.MISC, true);
    public static final GameRule<Boolean> IMMEDIATE_RESPAWN = registerBoolean("immediate_respawn", GameRuleCategory.PLAYER, false);
    public static final GameRule<Boolean> KEEP_INVENTORY = registerBoolean("keep_inventory", GameRuleCategory.PLAYER, false);
    public static final GameRule<Boolean> LAVA_SOURCE_CONVERSION = registerBoolean("lava_source_conversion", GameRuleCategory.UPDATES, false);
    public static final GameRule<Boolean> LIMITED_CRAFTING = registerBoolean("limited_crafting", GameRuleCategory.PLAYER, false);
    public static final GameRule<Boolean> LOCATOR_BAR = registerBoolean("locator_bar", GameRuleCategory.PLAYER, true);
    public static final GameRule<Boolean> LOG_ADMIN_COMMANDS = registerBoolean("log_admin_commands", GameRuleCategory.CHAT, true);
    public static final GameRule<Integer> MAX_BLOCK_MODIFICATIONS = registerInteger("max_block_modifications", GameRuleCategory.MISC, 32768, 1);
    public static final GameRule<Integer> MAX_COMMAND_FORKS = registerInteger("max_command_forks", GameRuleCategory.MISC, 65536, 0);
    public static final GameRule<Integer> MAX_COMMAND_SEQUENCE_LENGTH = registerInteger("max_command_sequence_length", GameRuleCategory.MISC, 65536, 0);
    public static final GameRule<Integer> MAX_ENTITY_CRAMMING = registerInteger("max_entity_cramming", GameRuleCategory.MOBS, 24, 0);
    public static final GameRule<Integer> MAX_MINECART_SPEED = registerInteger(
        "max_minecart_speed", GameRuleCategory.MISC, 8, 1, 1000, FeatureFlagSet.of(FeatureFlags.MINECART_IMPROVEMENTS)
    );
    public static final GameRule<Integer> MAX_SNOW_ACCUMULATION_HEIGHT = registerInteger("max_snow_accumulation_height", GameRuleCategory.UPDATES, 1, 0, 8);
    public static final GameRule<Boolean> MOB_DROPS = registerBoolean("mob_drops", GameRuleCategory.DROPS, true);
    public static final GameRule<Boolean> MOB_EXPLOSION_DROP_DECAY = registerBoolean("mob_explosion_drop_decay", GameRuleCategory.DROPS, true);
    public static final GameRule<Boolean> MOB_GRIEFING = registerBoolean("mob_griefing", GameRuleCategory.MOBS, true);
    public static final GameRule<Boolean> NATURAL_HEALTH_REGENERATION = registerBoolean("natural_health_regeneration", GameRuleCategory.PLAYER, true);
    public static final GameRule<Boolean> PLAYER_MOVEMENT_CHECK = registerBoolean("player_movement_check", GameRuleCategory.PLAYER, true);
    public static final GameRule<Integer> PLAYERS_NETHER_PORTAL_CREATIVE_DELAY = registerInteger(
        "players_nether_portal_creative_delay", GameRuleCategory.PLAYER, 0, 0
    );
    public static final GameRule<Integer> PLAYERS_NETHER_PORTAL_DEFAULT_DELAY = registerInteger(
        "players_nether_portal_default_delay", GameRuleCategory.PLAYER, 80, 0
    );
    public static final GameRule<Integer> PLAYERS_SLEEPING_PERCENTAGE = registerInteger("players_sleeping_percentage", GameRuleCategory.PLAYER, 100, 0);
    public static final GameRule<Boolean> PROJECTILES_CAN_BREAK_BLOCKS = registerBoolean("projectiles_can_break_blocks", GameRuleCategory.DROPS, true);
    public static final GameRule<Boolean> PVP = registerBoolean("pvp", GameRuleCategory.PLAYER, true);
    public static final GameRule<Boolean> RAIDS = registerBoolean("raids", GameRuleCategory.MOBS, true);
    public static final GameRule<Integer> RANDOM_TICK_SPEED = registerInteger("random_tick_speed", GameRuleCategory.UPDATES, 3, 0);
    public static final GameRule<Boolean> REDUCED_DEBUG_INFO = registerBoolean("reduced_debug_info", GameRuleCategory.MISC, false);
    public static final GameRule<Integer> RESPAWN_RADIUS = registerInteger("respawn_radius", GameRuleCategory.PLAYER, 10, 0);
    public static final GameRule<Boolean> SEND_COMMAND_FEEDBACK = registerBoolean("send_command_feedback", GameRuleCategory.CHAT, true);
    public static final GameRule<Boolean> SHOW_ADVANCEMENT_MESSAGES = registerBoolean("show_advancement_messages", GameRuleCategory.CHAT, true);
    public static final GameRule<Boolean> SHOW_DEATH_MESSAGES = registerBoolean("show_death_messages", GameRuleCategory.CHAT, true);
    public static final GameRule<Boolean> SPAWNER_BLOCKS_WORK = registerBoolean("spawner_blocks_work", GameRuleCategory.MISC, true);
    public static final GameRule<Boolean> SPAWN_MOBS = registerBoolean("spawn_mobs", GameRuleCategory.SPAWNING, true);
    public static final GameRule<Boolean> SPAWN_MONSTERS = registerBoolean("spawn_monsters", GameRuleCategory.SPAWNING, true);
    public static final GameRule<Boolean> SPAWN_PATROLS = registerBoolean("spawn_patrols", GameRuleCategory.SPAWNING, true);
    public static final GameRule<Boolean> SPAWN_PHANTOMS = registerBoolean("spawn_phantoms", GameRuleCategory.SPAWNING, true);
    public static final GameRule<Boolean> SPAWN_WANDERING_TRADERS = registerBoolean("spawn_wandering_traders", GameRuleCategory.SPAWNING, true);
    public static final GameRule<Boolean> SPAWN_WARDENS = registerBoolean("spawn_wardens", GameRuleCategory.SPAWNING, true);
    public static final GameRule<Boolean> SPECTATORS_GENERATE_CHUNKS = registerBoolean("spectators_generate_chunks", GameRuleCategory.PLAYER, true);
    public static final GameRule<Boolean> SPREAD_VINES = registerBoolean("spread_vines", GameRuleCategory.UPDATES, true);
    public static final GameRule<Boolean> TNT_EXPLODES = registerBoolean("tnt_explodes", GameRuleCategory.MISC, true);
    public static final GameRule<Boolean> TNT_EXPLOSION_DROP_DECAY = registerBoolean("tnt_explosion_drop_decay", GameRuleCategory.DROPS, false);
    public static final GameRule<Boolean> UNIVERSAL_ANGER = registerBoolean("universal_anger", GameRuleCategory.MOBS, false);
    public static final GameRule<Boolean> WATER_SOURCE_CONVERSION = registerBoolean("water_source_conversion", GameRuleCategory.UPDATES, true);
    public final GameRuleMap rules;

    public static Codec<GameRules> codec(FeatureFlagSet enabledFeatures) {
        return GameRuleMap.CODEC.xmap(rules -> new GameRules(enabledFeatures, rules), gameRules -> gameRules.rules);
    }

    public GameRules(FeatureFlagSet enabledFeatures, GameRuleMap rules) {
        this(enabledFeatures);
        this.rules.setFromIf(rules, this.rules::has);
    }

    public GameRules(FeatureFlagSet enabledFeatures) {
        this.rules = GameRuleMap.of(BuiltInRegistries.GAME_RULE.filterFeatures(enabledFeatures).listElements().map(Holder::value));
    }

    public Stream<GameRule<?>> availableRules() {
        return this.rules.keySet().stream();
    }

    public <T> T get(GameRule<T> rule) {
        T object = this.rules.get(rule);
        if (object == null) {
            throw new IllegalArgumentException("Tried to access invalid game rule");
        } else {
            return object;
        }
    }

    public <T> void set(GameRule<T> rule, T value, net.minecraft.server.level.@Nullable ServerLevel level) { // Paper - per-world game rules
        if (!this.rules.has(rule)) {
            throw new IllegalArgumentException("Tried to set invalid game rule");
        } else {
            this.rules.set(rule, value);
            // Paper start - per-world game rules
            if (level != null) {
                level.getServer().onGameRuleChanged(level, rule, value);
                // Paper end - per-world game rules
            }
        }
    }

    public GameRules copy(FeatureFlagSet enabledFeatures) {
        return new GameRules(enabledFeatures, this.rules);
    }

    // Paper start - per-world game rules
    public void setAll(GameRules gameRules, net.minecraft.server.level.@Nullable ServerLevel level) {
        this.setAll(gameRules.rules, level);
        // Paper end - per-world game rules
    }

    // Paper start - per-world game rules
    public void setAll(GameRuleMap rules, net.minecraft.server.level.@Nullable ServerLevel level) {
        rules.keySet().forEach(rule -> this.setFromOther(rules, (GameRule<?>)rule, level));
        // Paper end - per-world game rules
    }

    // Paper start - per-world game rules
    private <T> void setFromOther(GameRuleMap rules, GameRule<T> rule, net.minecraft.server.level.@Nullable ServerLevel level) {
        this.set(rule, Objects.requireNonNull(rules.get(rule)), level);
        // Paper end - per-world game rules
    }

    public void visitGameRuleTypes(GameRuleTypeVisitor visitor) {
        this.rules.keySet().forEach(gameRule -> {
            visitor.visit((GameRule<?>)gameRule);
            gameRule.callVisitor(visitor);
        });
    }

    private static GameRule<Boolean> registerBoolean(String name, GameRuleCategory category, boolean defaultValue) {
        return register(
            name,
            category,
            GameRuleType.BOOL,
            BoolArgumentType.bool(),
            Codec.BOOL,
            defaultValue,
            FeatureFlagSet.of(),
            GameRuleTypeVisitor::visitBoolean,
            _boolean -> _boolean ? 1 : 0
        );
    }

    private static GameRule<Integer> registerInteger(String name, GameRuleCategory category, int defaultValue, int min) {
        return registerInteger(name, category, defaultValue, min, Integer.MAX_VALUE, FeatureFlagSet.of());
    }

    private static GameRule<Integer> registerInteger(String name, GameRuleCategory category, int defaultValue, int min, int max) {
        return registerInteger(name, category, defaultValue, min, max, FeatureFlagSet.of());
    }

    private static GameRule<Integer> registerInteger(
        String name, GameRuleCategory category, int defaultValue, int min, int max, FeatureFlagSet requiredFeatures
    ) {
        return register(
            name,
            category,
            GameRuleType.INT,
            IntegerArgumentType.integer(min, max),
            Codec.intRange(min, max),
            defaultValue,
            requiredFeatures,
            GameRuleTypeVisitor::visitInteger,
            integer -> integer
        );
    }

    private static <T> GameRule<T> register(
        String name,
        GameRuleCategory category,
        GameRuleType type,
        ArgumentType<T> argument,
        Codec<T> valueCodec,
        T defaultValue,
        FeatureFlagSet requiredFeatures,
        GameRules.VisitorCaller<T> visitorCaller,
        ToIntFunction<T> commandResultFunction
    ) {
        return Registry.register(
            BuiltInRegistries.GAME_RULE,
            name,
            new GameRule<>(category, type, argument, visitorCaller, valueCodec, commandResultFunction, defaultValue, requiredFeatures)
        );
    }

    public static GameRule<?> bootstrap(Registry<GameRule<?>> registry) {
        return ADVANCE_TIME;
    }

    public <T> String getAsString(GameRule<T> rule) {
        return rule.serialize(this.get(rule));
    }

    public interface VisitorCaller<T> {
        void call(GameRuleTypeVisitor visitor, GameRule<T> rule);
    }
}
