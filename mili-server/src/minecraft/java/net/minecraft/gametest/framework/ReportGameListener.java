package net.minecraft.gametest.framework;

import com.google.common.base.MoreObjects;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import org.apache.commons.lang3.exception.ExceptionUtils;

class ReportGameListener implements GameTestListener {
    private int attempts = 0;
    private int successes = 0;

    public ReportGameListener() {
    }

    @Override
    public void testStructureLoaded(GameTestInfo testInfo) {
        this.attempts++;
    }

    private void handleRetry(GameTestInfo testInfo, GameTestRunner runner, boolean passed) {
        RetryOptions retryOptions = testInfo.retryOptions();
        String string = String.format(Locale.ROOT, "[Run: %4d, Ok: %4d, Fail: %4d", this.attempts, this.successes, this.attempts - this.successes);
        if (!retryOptions.unlimitedTries()) {
            string = string + String.format(Locale.ROOT, ", Left: %4d", retryOptions.numberOfTries() - this.attempts);
        }

        string = string + "]";
        String string1 = testInfo.id() + " " + (passed ? "passed" : "failed") + "! " + testInfo.getRunTime() + "ms";
        String string2 = String.format(Locale.ROOT, "%-53s%s", string, string1);
        if (passed) {
            reportPassed(testInfo, string2);
        } else {
            say(testInfo.getLevel(), ChatFormatting.RED, string2);
        }

        if (retryOptions.hasTriesLeft(this.attempts, this.successes)) {
            runner.rerunTest(testInfo);
        }
    }

    @Override
    public void testPassed(GameTestInfo test, GameTestRunner runner) {
        this.successes++;
        if (test.retryOptions().hasRetries()) {
            this.handleRetry(test, runner, true);
        } else if (!test.isFlaky()) {
            reportPassed(test, test.id() + " passed! (" + test.getRunTime() + "ms / " + test.getTick() + "gameticks)");
        } else {
            if (this.successes >= test.requiredSuccesses()) {
                reportPassed(test, test + " passed " + this.successes + " times of " + this.attempts + " attempts.");
            } else {
                say(test.getLevel(), ChatFormatting.GREEN, "Flaky test " + test + " succeeded, attempt: " + this.attempts + " successes: " + this.successes);
                runner.rerunTest(test);
            }
        }
    }

    @Override
    public void testFailed(GameTestInfo test, GameTestRunner runner) {
        if (!test.isFlaky()) {
            reportFailure(test, test.getError());
            if (test.retryOptions().hasRetries()) {
                this.handleRetry(test, runner, false);
            }
        } else {
            GameTestInstance test1 = test.getTest();
            String string = "Flaky test " + test + " failed, attempt: " + this.attempts + "/" + test1.maxAttempts();
            if (test1.requiredSuccesses() > 1) {
                string = string + ", successes: " + this.successes + " (" + test1.requiredSuccesses() + " required)";
            }

            say(test.getLevel(), ChatFormatting.YELLOW, string);
            if (test.maxAttempts() - this.attempts + this.successes >= test.requiredSuccesses()) {
                runner.rerunTest(test);
            } else {
                reportFailure(test, new ExhaustedAttemptsException(this.attempts, this.successes, test));
            }
        }
    }

    @Override
    public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
        newTest.addListener(this);
    }

    public static void reportPassed(GameTestInfo testInfo, String message) {
        getTestInstanceBlockEntity(testInfo).ifPresent(blockEntity -> blockEntity.setSuccess());
        visualizePassedTest(testInfo, message);
    }

    private static void visualizePassedTest(GameTestInfo testInfo, String message) {
        say(testInfo.getLevel(), ChatFormatting.GREEN, message);
        GlobalTestReporter.onTestSuccess(testInfo);
    }

    protected static void reportFailure(GameTestInfo testInfo, Throwable error) {
        Component description;
        if (error instanceof GameTestAssertException gameTestAssertException) {
            description = gameTestAssertException.getDescription();
        } else {
            description = Component.literal(Util.describeError(error));
        }

        getTestInstanceBlockEntity(testInfo).ifPresent(blockEntity -> blockEntity.setErrorMessage(description));
        visualizeFailedTest(testInfo, error);
    }

    protected static void visualizeFailedTest(GameTestInfo testInfo, Throwable error) {
        String string = error.getMessage() + (error.getCause() == null ? "" : " cause: " + Util.describeError(error.getCause()));
        String string1 = (testInfo.isRequired() ? "" : "(optional) ") + testInfo.id() + " failed! " + string;
        say(testInfo.getLevel(), testInfo.isRequired() ? ChatFormatting.RED : ChatFormatting.YELLOW, string1);
        Throwable throwable = MoreObjects.firstNonNull(ExceptionUtils.getRootCause(error), error);
        if (throwable instanceof GameTestAssertPosException gameTestAssertPosException) {
            testInfo.getTestInstanceBlockEntity().markError(gameTestAssertPosException.getAbsolutePos(), gameTestAssertPosException.getMessageToShowAtBlock());
        }

        GlobalTestReporter.onTestFailed(testInfo);
    }

    private static Optional<TestInstanceBlockEntity> getTestInstanceBlockEntity(GameTestInfo testInfo) {
        ServerLevel level = testInfo.getLevel();
        Optional<BlockPos> optional = Optional.ofNullable(testInfo.getTestBlockPos());
        return optional.flatMap(pos -> level.getBlockEntity(pos, BlockEntityType.TEST_INSTANCE_BLOCK));
    }

    protected static void say(ServerLevel level, ChatFormatting formatting, String message) {
        level.getPlayers(player -> true).forEach(player -> player.sendSystemMessage(Component.literal(message).withStyle(formatting)));
    }
}
