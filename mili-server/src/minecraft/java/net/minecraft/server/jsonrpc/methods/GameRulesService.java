package net.minecraft.server.jsonrpc.methods;

import com.mojang.datafixers.kinds.App;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleType;

public class GameRulesService {
    public static List<GameRulesService.GameRuleUpdate<?>> get(MinecraftApi api) {
        List<GameRulesService.GameRuleUpdate<?>> list = new ArrayList<>();
        api.gameRuleService().getAvailableGameRules().forEach(gameRule -> addGameRule(api, (GameRule<?>)gameRule, list));
        return list;
    }

    private static <T> void addGameRule(MinecraftApi api, GameRule<T> rule, List<GameRulesService.GameRuleUpdate<?>> updates) {
        T ruleValue = api.gameRuleService().getRuleValue(rule);
        updates.add(getTypedRule(api, rule, Objects.requireNonNull(ruleValue)));
    }

    public static <T> GameRulesService.GameRuleUpdate<T> getTypedRule(MinecraftApi api, GameRule<T> rule, T value) {
        return api.gameRuleService().getTypedRule(rule, value);
    }

    public static <T> GameRulesService.GameRuleUpdate<T> update(MinecraftApi api, GameRulesService.GameRuleUpdate<T> update, ClientInfo client) {
        return api.gameRuleService().updateGameRule(update, client);
    }

    public record GameRuleUpdate<T>(GameRule<T> gameRule, T value) {
        public static final Codec<GameRulesService.GameRuleUpdate<?>> TYPED_CODEC = BuiltInRegistries.GAME_RULE
            .byNameCodec()
            .dispatch("key", GameRulesService.GameRuleUpdate::gameRule, GameRulesService.GameRuleUpdate::getValueAndTypeCodec);
        public static final Codec<GameRulesService.GameRuleUpdate<?>> CODEC = BuiltInRegistries.GAME_RULE
            .byNameCodec()
            .dispatch("key", GameRulesService.GameRuleUpdate::gameRule, GameRulesService.GameRuleUpdate::getValueCodec);

        private static <T> MapCodec<? extends GameRulesService.GameRuleUpdate<T>> getValueCodec(GameRule<T> gameRule) {
            return gameRule.valueCodec()
                .fieldOf("value")
                .xmap(object -> new GameRulesService.GameRuleUpdate<>(gameRule, (T)object), GameRulesService.GameRuleUpdate::value);
        }

        private static <T> MapCodec<? extends GameRulesService.GameRuleUpdate<T>> getValueAndTypeCodec(GameRule<T> gameRule) {
            return RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        StringRepresentable.fromEnum(GameRuleType::values)
                            .fieldOf("type")
                            .forGetter(gameRuleUpdate -> gameRuleUpdate.gameRule.gameRuleType()),
                        gameRule.valueCodec()
                            .fieldOf("value")
                            .forGetter(GameRulesService.GameRuleUpdate::value)
                    )
                    .apply(instance, (gameRuleType, object) -> getUntypedRule(gameRule, gameRuleType, object))
            );
        }

        private static <T> GameRulesService.GameRuleUpdate<T> getUntypedRule(GameRule<T> gameRule, GameRuleType type, T value) {
            if (gameRule.gameRuleType() != type) {
                throw new InvalidParameterJsonRpcException(
                    "Stated type \"" + type + "\" mismatches with actual type \"" + gameRule.gameRuleType() + "\" of gamerule \"" + gameRule.id() + "\""
                );
            } else {
                return new GameRulesService.GameRuleUpdate<>(gameRule, value);
            }
        }
    }
}
