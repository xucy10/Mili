package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MultipleTestTracker {
    private static final char NOT_STARTED_TEST_CHAR = ' ';
    private static final char ONGOING_TEST_CHAR = '_';
    private static final char SUCCESSFUL_TEST_CHAR = '+';
    private static final char FAILED_OPTIONAL_TEST_CHAR = 'x';
    private static final char FAILED_REQUIRED_TEST_CHAR = 'X';
    private final Collection<GameTestInfo> tests = Lists.newArrayList();
    private final Collection<GameTestListener> listeners = Lists.newArrayList();

    public MultipleTestTracker() {
    }

    public MultipleTestTracker(Collection<GameTestInfo> testInfos) {
        this.tests.addAll(testInfos);
    }

    public void addTestToTrack(GameTestInfo testInfo) {
        this.tests.add(testInfo);
        this.listeners.forEach(testInfo::addListener);
    }

    public void addListener(GameTestListener testListener) {
        this.listeners.add(testListener);
        this.tests.forEach(testInfo -> testInfo.addListener(testListener));
    }

    public void addFailureListener(final Consumer<GameTestInfo> onFail) {
        this.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {
            }

            @Override
            public void testPassed(GameTestInfo test, GameTestRunner runner) {
            }

            @Override
            public void testFailed(GameTestInfo test, GameTestRunner runner) {
                onFail.accept(test);
            }

            @Override
            public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            }
        });
    }

    public int getFailedRequiredCount() {
        return (int)this.tests.stream().filter(GameTestInfo::hasFailed).filter(GameTestInfo::isRequired).count();
    }

    public int getFailedOptionalCount() {
        return (int)this.tests.stream().filter(GameTestInfo::hasFailed).filter(GameTestInfo::isOptional).count();
    }

    public int getDoneCount() {
        return (int)this.tests.stream().filter(GameTestInfo::isDone).count();
    }

    public boolean hasFailedRequired() {
        return this.getFailedRequiredCount() > 0;
    }

    public boolean hasFailedOptional() {
        return this.getFailedOptionalCount() > 0;
    }

    public Collection<GameTestInfo> getFailedRequired() {
        return this.tests.stream().filter(GameTestInfo::hasFailed).filter(GameTestInfo::isRequired).collect(Collectors.toList());
    }

    public Collection<GameTestInfo> getFailedOptional() {
        return this.tests.stream().filter(GameTestInfo::hasFailed).filter(GameTestInfo::isOptional).collect(Collectors.toList());
    }

    public int getTotalCount() {
        return this.tests.size();
    }

    public boolean isDone() {
        return this.getDoneCount() == this.getTotalCount();
    }

    public String getProgressBar() {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append('[');
        this.tests.forEach(testInfo -> {
            if (!testInfo.hasStarted()) {
                stringBuffer.append(' ');
            } else if (testInfo.hasSucceeded()) {
                stringBuffer.append('+');
            } else if (testInfo.hasFailed()) {
                stringBuffer.append((char)(testInfo.isRequired() ? 'X' : 'x'));
            } else {
                stringBuffer.append('_');
            }
        });
        stringBuffer.append(']');
        return stringBuffer.toString();
    }

    @Override
    public String toString() {
        return this.getProgressBar();
    }

    public void remove(GameTestInfo gameTestInfo) {
        this.tests.remove(gameTestInfo);
    }
}
