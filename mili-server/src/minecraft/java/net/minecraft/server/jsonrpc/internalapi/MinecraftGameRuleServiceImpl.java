package net.minecraft.server.jsonrpc.internalapi;

import java.util.stream.Stream;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.GameRulesService;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

public class MinecraftGameRuleServiceImpl implements MinecraftGameRuleService {
    private final DedicatedServer server;
    private final GameRules gameRules;
    private final JsonRpcLogger jsonrpcLogger;

    public MinecraftGameRuleServiceImpl(DedicatedServer server, JsonRpcLogger jsonrpcLogger) {
        this.server = server;
        this.gameRules = server.getWorldData().getGameRules();
        this.jsonrpcLogger = jsonrpcLogger;
    }

    @Override
    public <T> GameRulesService.GameRuleUpdate<T> updateGameRule(GameRulesService.GameRuleUpdate<T> update, ClientInfo client) {
        GameRule<T> gameRule = update.gameRule();
        T object = this.gameRules.get(gameRule);
        T object1 = update.value();
        this.gameRules.set(gameRule, object1, this.server.overworld()); // Paper - per-world game rules - use overworld for vanilla protocol
        this.jsonrpcLogger.log(client, "Game rule '{}' updated from '{}' to '{}'", gameRule.id(), gameRule.serialize(object), gameRule.serialize(object1));
        return update;
    }

    @Override
    public <T> GameRulesService.GameRuleUpdate<T> getTypedRule(GameRule<T> rule, T value) {
        return new GameRulesService.GameRuleUpdate<>(rule, value);
    }

    @Override
    public Stream<GameRule<?>> getAvailableGameRules() {
        return this.gameRules.availableRules();
    }

    @Override
    public <T> T getRuleValue(GameRule<T> rule) {
        return this.gameRules.get(rule);
    }
}
