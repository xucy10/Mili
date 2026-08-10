package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

public class GameTestBatchFactory {
    private static final int MAX_TESTS_PER_BATCH = 50;
    public static final GameTestBatchFactory.TestDecorator DIRECT = (instance, level) -> Stream.of(
        new GameTestInfo(instance, Rotation.NONE, level, RetryOptions.noRetries())
    );

    public static List<GameTestBatch> divideIntoBatches(
        Collection<Holder.Reference<GameTestInstance>> instances, GameTestBatchFactory.TestDecorator decorator, ServerLevel level
    ) {
        Map<Holder<TestEnvironmentDefinition>, List<GameTestInfo>> map = instances.stream()
            .flatMap(instance -> decorator.decorate((Holder.Reference<GameTestInstance>)instance, level))
            .collect(Collectors.groupingBy(testInfo -> testInfo.getTest().batch()));
        return map.entrySet().stream().flatMap(entry -> {
            Holder<TestEnvironmentDefinition> holder = entry.getKey();
            List<GameTestInfo> list = entry.getValue();
            return Streams.mapWithIndex(Lists.partition(list, 50).stream(), (gameTestInfos, index) -> toGameTestBatch(gameTestInfos, holder, (int)index));
        }).toList();
    }

    public static GameTestRunner.GameTestBatcher fromGameTestInfo() {
        return fromGameTestInfo(50);
    }

    public static GameTestRunner.GameTestBatcher fromGameTestInfo(int maxTests) {
        return infos -> {
            Map<Holder<TestEnvironmentDefinition>, List<GameTestInfo>> map = infos.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(gameTestInfo -> gameTestInfo.getTest().batch()));
            return map.entrySet().stream().flatMap(entry -> {
                Holder<TestEnvironmentDefinition> holder = entry.getKey();
                List<GameTestInfo> list = entry.getValue();
                return Streams.mapWithIndex(Lists.partition(list, maxTests).stream(), (list1, l) -> toGameTestBatch(List.copyOf(list1), holder, (int)l));
            }).toList();
        };
    }

    public static GameTestBatch toGameTestBatch(Collection<GameTestInfo> gameTestInfos, Holder<TestEnvironmentDefinition> environment, int index) {
        return new GameTestBatch(index, gameTestInfos, environment);
    }

    @FunctionalInterface
    public interface TestDecorator {
        Stream<GameTestInfo> decorate(Holder.Reference<GameTestInstance> instance, ServerLevel level);
    }
}
