package net.minecraft.gametest.framework;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public record GeneratedTest(
    Map<Identifier, TestData<ResourceKey<TestEnvironmentDefinition>>> tests,
    ResourceKey<Consumer<GameTestHelper>> functionKey,
    Consumer<GameTestHelper> function
) {
    public GeneratedTest(Map<Identifier, TestData<ResourceKey<TestEnvironmentDefinition>>> tests, Identifier functionKey, Consumer<GameTestHelper> function) {
        this(tests, ResourceKey.create(Registries.TEST_FUNCTION, functionKey), function);
    }

    public GeneratedTest(Identifier functionKey, TestData<ResourceKey<TestEnvironmentDefinition>> testData, Consumer<GameTestHelper> function) {
        this(Map.of(functionKey, testData), functionKey, function);
    }
}
