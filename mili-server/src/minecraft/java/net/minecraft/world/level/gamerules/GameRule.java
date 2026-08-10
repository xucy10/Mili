package net.minecraft.world.level.gamerules;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Objects;
import java.util.function.ToIntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlagSet;

public final class GameRule<T> implements FeatureElement {
    private final GameRuleCategory category;
    private final GameRuleType gameRuleType;
    private final ArgumentType<T> argument;
    private final GameRules.VisitorCaller<T> visitorCaller;
    private final Codec<T> valueCodec;
    private final ToIntFunction<T> commandResultFunction;
    private final T defaultValue;
    private final FeatureFlagSet requiredFeatures;

    public GameRule(
        GameRuleCategory category,
        GameRuleType gameRuleType,
        ArgumentType<T> argument,
        GameRules.VisitorCaller<T> visitorCaller,
        Codec<T> valueCodec,
        ToIntFunction<T> commandResultFunction,
        T defaultValue,
        FeatureFlagSet requiredFeatures
    ) {
        this.category = category;
        this.gameRuleType = gameRuleType;
        this.argument = argument;
        this.visitorCaller = visitorCaller;
        this.valueCodec = valueCodec;
        this.commandResultFunction = commandResultFunction;
        this.defaultValue = defaultValue;
        this.requiredFeatures = requiredFeatures;
    // Paper start - array backed gamerule access
    // Is expected to work as gamerules are in BuiltInRegistries and hence are not re-created during the servers' lifecycle.
        this.gameRuleIndex = LAST_GAMERULE_INDEX++;
    }
    public final int gameRuleIndex;
    public static int LAST_GAMERULE_INDEX = 0;
    // Paper end - array backed gamerule access

    @Override
    public String toString() {
        return this.id();
    }

    public String id() {
        return this.getIdentifier().toShortString();
    }

    public Identifier getIdentifier() {
        return Objects.requireNonNull(BuiltInRegistries.GAME_RULE.getKey(this));
    }

    public String getDescriptionId() {
        return Util.makeDescriptionId("gamerule", this.getIdentifier());
    }

    public String serialize(T value) {
        return value.toString();
    }

    public DataResult<T> deserialize(String value) {
        try {
            StringReader stringReader = new StringReader(value);
            T object = this.argument.parse(stringReader);
            return stringReader.canRead() ? DataResult.error(() -> "Failed to deserialize; trailing characters", object) : DataResult.success(object);
        } catch (CommandSyntaxException var4) {
            return DataResult.error(() -> "Failed to deserialize");
        }
    }

    public Class<T> valueClass() {
        return (Class<T>)this.defaultValue.getClass();
    }

    public void callVisitor(GameRuleTypeVisitor visitor) {
        this.visitorCaller.call(visitor, this);
    }

    public int getCommandResult(T value) {
        return this.commandResultFunction.applyAsInt(value);
    }

    public GameRuleCategory category() {
        return this.category;
    }

    public GameRuleType gameRuleType() {
        return this.gameRuleType;
    }

    public ArgumentType<T> argument() {
        return this.argument;
    }

    public Codec<T> valueCodec() {
        return this.valueCodec;
    }

    public T defaultValue() {
        return this.defaultValue;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }
}
