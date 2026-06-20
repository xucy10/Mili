package net.minecraft.commands.arguments.selector;

import com.google.common.primitives.Doubles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSetSupplier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EntitySelectorParser {
    public static final char SYNTAX_SELECTOR_START = '@';
    private static final char SYNTAX_OPTIONS_START = '[';
    private static final char SYNTAX_OPTIONS_END = ']';
    public static final char SYNTAX_OPTIONS_KEY_VALUE_SEPARATOR = '=';
    private static final char SYNTAX_OPTIONS_SEPARATOR = ',';
    public static final char SYNTAX_NOT = '!';
    public static final char SYNTAX_TAG = '#';
    private static final char SELECTOR_NEAREST_PLAYER = 'p';
    private static final char SELECTOR_ALL_PLAYERS = 'a';
    private static final char SELECTOR_RANDOM_PLAYERS = 'r';
    private static final char SELECTOR_CURRENT_ENTITY = 's';
    private static final char SELECTOR_ALL_ENTITIES = 'e';
    private static final char SELECTOR_NEAREST_ENTITY = 'n';
    public static final SimpleCommandExceptionType ERROR_INVALID_NAME_OR_UUID = new SimpleCommandExceptionType(
        Component.translatable("argument.entity.invalid")
    );
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_SELECTOR_TYPE = new DynamicCommandExceptionType(
        type -> Component.translatableEscape("argument.entity.selector.unknown", type)
    );
    public static final SimpleCommandExceptionType ERROR_SELECTORS_NOT_ALLOWED = new SimpleCommandExceptionType(
        Component.translatable("argument.entity.selector.not_allowed")
    );
    public static final SimpleCommandExceptionType ERROR_MISSING_SELECTOR_TYPE = new SimpleCommandExceptionType(
        Component.translatable("argument.entity.selector.missing")
    );
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_OPTIONS = new SimpleCommandExceptionType(
        Component.translatable("argument.entity.options.unterminated")
    );
    public static final DynamicCommandExceptionType ERROR_EXPECTED_OPTION_VALUE = new DynamicCommandExceptionType(
        value -> Component.translatableEscape("argument.entity.options.valueless", value)
    );
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_NEAREST = (pos, entities) -> entities.sort(
        (entity1, entity2) -> Doubles.compare(entity1.distanceToSqr(pos), entity2.distanceToSqr(pos))
    );
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_FURTHEST = (pos, entities) -> entities.sort(
        (entity1, entity2) -> Doubles.compare(entity2.distanceToSqr(pos), entity1.distanceToSqr(pos))
    );
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_RANDOM = (pos, entities) -> Collections.shuffle(entities);
    public static final BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> SUGGEST_NOTHING = (builder, consumer) -> builder.buildFuture();
    private final StringReader reader;
    private final boolean allowSelectors;
    private int maxResults;
    private boolean includesEntities;
    private boolean worldLimited;
    private MinMaxBounds.@Nullable Doubles distance;
    private MinMaxBounds.@Nullable Ints level;
    private @Nullable Double x;
    private @Nullable Double y;
    private @Nullable Double z;
    private @Nullable Double deltaX;
    private @Nullable Double deltaY;
    private @Nullable Double deltaZ;
    private MinMaxBounds.@Nullable FloatDegrees rotX;
    private MinMaxBounds.@Nullable FloatDegrees rotY;
    private final List<Predicate<Entity>> predicates = new ArrayList<>();
    private BiConsumer<Vec3, List<? extends Entity>> order = EntitySelector.ORDER_ARBITRARY;
    private boolean currentEntity;
    private @Nullable String playerName;
    private int startPosition;
    private @Nullable UUID entityUUID;
    private BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> suggestions = SUGGEST_NOTHING;
    private boolean hasNameEquals;
    private boolean hasNameNotEquals;
    private boolean isLimited;
    private boolean isSorted;
    private boolean hasGamemodeEquals;
    private boolean hasGamemodeNotEquals;
    private boolean hasTeamEquals;
    private boolean hasTeamNotEquals;
    private @Nullable EntityType<?> type;
    private boolean typeInverse;
    private boolean hasScores;
    private boolean hasAdvancements;
    private boolean usesSelectors;

    public EntitySelectorParser(StringReader reader, boolean allowSelectors) {
        this.reader = reader;
        this.allowSelectors = allowSelectors;
    }

    public static <S> boolean allowSelectors(S suggestionProvider) {
        // Paper start - Fix EntityArgument permissions
        if (suggestionProvider instanceof net.minecraft.commands.CommandSourceStack stack) {
            return stack.bypassSelectorPermissions || stack.hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS, "minecraft.command.selector");
        }
        // Paper end - Fix EntityArgument permissions
        return suggestionProvider instanceof PermissionSetSupplier permissionSetSupplier
            && permissionSetSupplier.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS);
    }

    @Deprecated
    public static boolean allowSelectors(PermissionSetSupplier permissionsSupplier) {
        return permissionsSupplier.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS);
    }

    public EntitySelector getSelector() {
        AABB aabb;
        if (this.deltaX == null && this.deltaY == null && this.deltaZ == null) {
            if (this.distance != null && this.distance.max().isPresent()) {
                double d = (Double)this.distance.max().get();
                aabb = new AABB(-d, -d, -d, d + 1.0, d + 1.0, d + 1.0);
            } else {
                aabb = null;
            }
        } else {
            aabb = this.createAabb(this.deltaX == null ? 0.0 : this.deltaX, this.deltaY == null ? 0.0 : this.deltaY, this.deltaZ == null ? 0.0 : this.deltaZ);
        }

        Function<Vec3, Vec3> function;
        if (this.x == null && this.y == null && this.z == null) {
            function = vec3 -> vec3;
        } else {
            function = vec3 -> new Vec3(this.x == null ? vec3.x : this.x, this.y == null ? vec3.y : this.y, this.z == null ? vec3.z : this.z);
        }

        return new EntitySelector(
            this.maxResults,
            this.includesEntities,
            this.worldLimited,
            List.copyOf(this.predicates),
            this.distance,
            function,
            aabb,
            this.order,
            this.currentEntity,
            this.playerName,
            this.entityUUID,
            this.type,
            this.usesSelectors
        );
    }

    private AABB createAabb(double sizeX, double sizeY, double sizeZ) {
        boolean flag = sizeX < 0.0;
        boolean flag1 = sizeY < 0.0;
        boolean flag2 = sizeZ < 0.0;
        double d = flag ? sizeX : 0.0;
        double d1 = flag1 ? sizeY : 0.0;
        double d2 = flag2 ? sizeZ : 0.0;
        double d3 = (flag ? 0.0 : sizeX) + 1.0;
        double d4 = (flag1 ? 0.0 : sizeY) + 1.0;
        double d5 = (flag2 ? 0.0 : sizeZ) + 1.0;
        return new AABB(d, d1, d2, d3, d4, d5);
    }

    private void finalizePredicates() {
        if (this.rotX != null) {
            this.predicates.add(this.createRotationPredicate(this.rotX, Entity::getXRot));
        }

        if (this.rotY != null) {
            this.predicates.add(this.createRotationPredicate(this.rotY, Entity::getYRot));
        }

        if (this.level != null) {
            this.predicates.add(entity -> entity instanceof ServerPlayer serverPlayer && this.level.matches(serverPlayer.experienceLevel));
        }
    }

    private Predicate<Entity> createRotationPredicate(MinMaxBounds.FloatDegrees bounds, ToFloatFunction<Entity> angleGetter) {
        float f = Mth.wrapDegrees(bounds.min().orElse(0.0F));
        float f1 = Mth.wrapDegrees(bounds.max().orElse(359.0F));
        return entity -> {
            float f2 = Mth.wrapDegrees(angleGetter.applyAsFloat(entity));
            return f > f1 ? f2 >= f || f2 <= f1 : f2 >= f && f2 <= f1;
        };
    }

    // CraftBukkit start
    protected void parseSelector(boolean overridePermissions) throws CommandSyntaxException {
        this.usesSelectors = !overridePermissions;
        // CraftBukkit end
        this.suggestions = this::suggestSelector;
        if (!this.reader.canRead()) {
            throw ERROR_MISSING_SELECTOR_TYPE.createWithContext(this.reader);
        } else {
            int cursor = this.reader.getCursor();
            char c = this.reader.read();

            if (switch (c) {
                case 'a' -> {
                    this.maxResults = Integer.MAX_VALUE;
                    this.includesEntities = false;
                    this.order = EntitySelector.ORDER_ARBITRARY;
                    this.limitToType(EntityType.PLAYER);
                    yield false;
                }
                default -> {
                    this.reader.setCursor(cursor);
                    throw ERROR_UNKNOWN_SELECTOR_TYPE.createWithContext(this.reader, "@" + c);
                }
                case 'e' -> {
                    this.maxResults = Integer.MAX_VALUE;
                    this.includesEntities = true;
                    this.order = EntitySelector.ORDER_ARBITRARY;
                    yield true;
                }
                case 'n' -> {
                    this.maxResults = 1;
                    this.includesEntities = true;
                    this.order = ORDER_NEAREST;
                    yield true;
                }
                case 'p' -> {
                    this.maxResults = 1;
                    this.includesEntities = false;
                    this.order = ORDER_NEAREST;
                    this.limitToType(EntityType.PLAYER);
                    yield false;
                }
                case 'r' -> {
                    this.maxResults = 1;
                    this.includesEntities = false;
                    this.order = ORDER_RANDOM;
                    this.limitToType(EntityType.PLAYER);
                    yield false;
                }
                case 's' -> {
                    this.maxResults = 1;
                    this.includesEntities = true;
                    this.currentEntity = true;
                    yield false;
                }
            }) {
                this.predicates.add(Entity::isAlive);
            }

            this.suggestions = this::suggestOpenOptions;
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.reader.skip();
                this.suggestions = this::suggestOptionsKeyOrClose;
                this.parseOptions();
            }
        }
    }

    protected void parseNameOrUUID() throws CommandSyntaxException {
        if (this.reader.canRead()) {
            this.suggestions = this::suggestName;
        }

        int cursor = this.reader.getCursor();
        String string = this.reader.readString();

        try {
            this.entityUUID = UUID.fromString(string);
            this.includesEntities = true;
        } catch (IllegalArgumentException var4) {
            if (string.isEmpty() || string.length() > 16) {
                this.reader.setCursor(cursor);
                throw ERROR_INVALID_NAME_OR_UUID.createWithContext(this.reader);
            }

            this.includesEntities = false;
            this.playerName = string;
        }

        this.maxResults = 1;
    }

    protected void parseOptions() throws CommandSyntaxException {
        this.suggestions = this::suggestOptionsKey;
        this.reader.skipWhitespace();

        while (this.reader.canRead() && this.reader.peek() != ']') {
            this.reader.skipWhitespace();
            int cursor = this.reader.getCursor();
            String string = this.reader.readString();
            EntitySelectorOptions.Modifier modifier = EntitySelectorOptions.get(this, string, cursor);
            this.reader.skipWhitespace();
            if (!this.reader.canRead() || this.reader.peek() != '=') {
                this.reader.setCursor(cursor);
                throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(this.reader, string);
            }

            this.reader.skip();
            this.reader.skipWhitespace();
            this.suggestions = SUGGEST_NOTHING;
            modifier.handle(this);
            this.reader.skipWhitespace();
            this.suggestions = this::suggestOptionsNextOrClose;
            if (this.reader.canRead()) {
                if (this.reader.peek() != ',') {
                    if (this.reader.peek() != ']') {
                        throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(this.reader);
                    }
                    break;
                }

                this.reader.skip();
                this.suggestions = this::suggestOptionsKey;
            }
        }

        if (this.reader.canRead()) {
            this.reader.skip();
            this.suggestions = SUGGEST_NOTHING;
        } else {
            throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(this.reader);
        }
    }

    public boolean shouldInvertValue() {
        this.reader.skipWhitespace();
        if (this.reader.canRead() && this.reader.peek() == '!') {
            this.reader.skip();
            this.reader.skipWhitespace();
            return true;
        } else {
            return false;
        }
    }

    public boolean isTag() {
        this.reader.skipWhitespace();
        if (this.reader.canRead() && this.reader.peek() == '#') {
            this.reader.skip();
            this.reader.skipWhitespace();
            return true;
        } else {
            return false;
        }
    }

    public StringReader getReader() {
        return this.reader;
    }

    public void addPredicate(Predicate<Entity> predicate) {
        this.predicates.add(predicate);
    }

    public void setWorldLimited() {
        this.worldLimited = true;
    }

    public MinMaxBounds.@Nullable Doubles getDistance() {
        return this.distance;
    }

    public void setDistance(MinMaxBounds.Doubles distance) {
        this.distance = distance;
    }

    public MinMaxBounds.@Nullable Ints getLevel() {
        return this.level;
    }

    public void setLevel(MinMaxBounds.Ints level) {
        this.level = level;
    }

    public MinMaxBounds.@Nullable FloatDegrees getRotX() {
        return this.rotX;
    }

    public void setRotX(MinMaxBounds.FloatDegrees rotX) {
        this.rotX = rotX;
    }

    public MinMaxBounds.@Nullable FloatDegrees getRotY() {
        return this.rotY;
    }

    public void setRotY(MinMaxBounds.FloatDegrees rotY) {
        this.rotY = rotY;
    }

    public @Nullable Double getX() {
        return this.x;
    }

    public @Nullable Double getY() {
        return this.y;
    }

    public @Nullable Double getZ() {
        return this.z;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public void setDeltaX(double deltaX) {
        this.deltaX = deltaX;
    }

    public void setDeltaY(double deltaY) {
        this.deltaY = deltaY;
    }

    public void setDeltaZ(double deltaZ) {
        this.deltaZ = deltaZ;
    }

    public @Nullable Double getDeltaX() {
        return this.deltaX;
    }

    public @Nullable Double getDeltaY() {
        return this.deltaY;
    }

    public @Nullable Double getDeltaZ() {
        return this.deltaZ;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public void setIncludesEntities(boolean includesEntities) {
        this.includesEntities = includesEntities;
    }

    public BiConsumer<Vec3, List<? extends Entity>> getOrder() {
        return this.order;
    }

    public void setOrder(BiConsumer<Vec3, List<? extends Entity>> order) {
        this.order = order;
    }

    public EntitySelector parse() throws CommandSyntaxException {
        // CraftBukkit start
        return this.parse(false);
    }

    public EntitySelector parse(boolean overridePermissions) throws CommandSyntaxException {
        // CraftBukkit end
        this.startPosition = this.reader.getCursor();
        this.suggestions = this::suggestNameOrSelector;
        if (this.reader.canRead() && this.reader.peek() == '@') {
            if (!this.allowSelectors) {
                throw ERROR_SELECTORS_NOT_ALLOWED.createWithContext(this.reader);
            }

            this.reader.skip();
            this.parseSelector(overridePermissions); // CraftBukkit
        } else {
            this.parseNameOrUUID();
        }

        this.finalizePredicates();
        return this.getSelector();
    }

    private static void fillSelectorSuggestions(SuggestionsBuilder builder) {
        builder.suggest("@p", Component.translatable("argument.entity.selector.nearestPlayer"));
        builder.suggest("@a", Component.translatable("argument.entity.selector.allPlayers"));
        builder.suggest("@r", Component.translatable("argument.entity.selector.randomPlayer"));
        builder.suggest("@s", Component.translatable("argument.entity.selector.self"));
        builder.suggest("@e", Component.translatable("argument.entity.selector.allEntities"));
        builder.suggest("@n", Component.translatable("argument.entity.selector.nearestEntity"));
    }

    private CompletableFuture<Suggestions> suggestNameOrSelector(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        consumer.accept(builder);
        if (this.allowSelectors) {
            fillSelectorSuggestions(builder);
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestName(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        SuggestionsBuilder suggestionsBuilder = builder.createOffset(this.startPosition);
        consumer.accept(suggestionsBuilder);
        return builder.add(suggestionsBuilder).buildFuture();
    }

    private CompletableFuture<Suggestions> suggestSelector(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        SuggestionsBuilder suggestionsBuilder = builder.createOffset(builder.getStart() - 1);
        fillSelectorSuggestions(suggestionsBuilder);
        builder.add(suggestionsBuilder);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenOptions(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest(String.valueOf('['));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionsKeyOrClose(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest(String.valueOf(']'));
        EntitySelectorOptions.suggestNames(this, builder);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionsKey(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        EntitySelectorOptions.suggestNames(this, builder);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionsNextOrClose(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest(String.valueOf(','));
        builder.suggest(String.valueOf(']'));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestEquals(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest(String.valueOf('='));
        return builder.buildFuture();
    }

    public boolean isCurrentEntity() {
        return this.currentEntity;
    }

    public void setSuggestions(BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> suggestionHandler) {
        this.suggestions = suggestionHandler;
    }

    public CompletableFuture<Suggestions> fillSuggestions(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        return this.suggestions.apply(builder.createOffset(this.reader.getCursor()), consumer);
    }

    public boolean hasNameEquals() {
        return this.hasNameEquals;
    }

    public void setHasNameEquals(boolean hasNameEquals) {
        this.hasNameEquals = hasNameEquals;
    }

    public boolean hasNameNotEquals() {
        return this.hasNameNotEquals;
    }

    public void setHasNameNotEquals(boolean hasNameNotEquals) {
        this.hasNameNotEquals = hasNameNotEquals;
    }

    public boolean isLimited() {
        return this.isLimited;
    }

    public void setLimited(boolean isLimited) {
        this.isLimited = isLimited;
    }

    public boolean isSorted() {
        return this.isSorted;
    }

    public void setSorted(boolean isSorted) {
        this.isSorted = isSorted;
    }

    public boolean hasGamemodeEquals() {
        return this.hasGamemodeEquals;
    }

    public void setHasGamemodeEquals(boolean hasGamemodeEquals) {
        this.hasGamemodeEquals = hasGamemodeEquals;
    }

    public boolean hasGamemodeNotEquals() {
        return this.hasGamemodeNotEquals;
    }

    public void setHasGamemodeNotEquals(boolean hasGamemodeNotEquals) {
        this.hasGamemodeNotEquals = hasGamemodeNotEquals;
    }

    public boolean hasTeamEquals() {
        return this.hasTeamEquals;
    }

    public void setHasTeamEquals(boolean hasTeamEquals) {
        this.hasTeamEquals = hasTeamEquals;
    }

    public boolean hasTeamNotEquals() {
        return this.hasTeamNotEquals;
    }

    public void setHasTeamNotEquals(boolean hasTeamNotEquals) {
        this.hasTeamNotEquals = hasTeamNotEquals;
    }

    public void limitToType(EntityType<?> type) {
        this.type = type;
    }

    public void setTypeLimitedInversely() {
        this.typeInverse = true;
    }

    public boolean isTypeLimited() {
        return this.type != null;
    }

    public boolean isTypeLimitedInversely() {
        return this.typeInverse;
    }

    public boolean hasScores() {
        return this.hasScores;
    }

    public void setHasScores(boolean hasScores) {
        this.hasScores = hasScores;
    }

    public boolean hasAdvancements() {
        return this.hasAdvancements;
    }

    public void setHasAdvancements(boolean hasAdvancements) {
        this.hasAdvancements = hasAdvancements;
    }
}
