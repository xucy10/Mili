package net.minecraft.gametest.framework;

class ExhaustedAttemptsException extends Throwable {
    public ExhaustedAttemptsException(int madeAttempts, int successfulAttempts, GameTestInfo testInfo) {
        super(
            "Not enough successes: "
                + successfulAttempts
                + " out of "
                + madeAttempts
                + " attempts. Required successes: "
                + testInfo.requiredSuccesses()
                + ". max attempts: "
                + testInfo.maxAttempts()
                + ".",
            testInfo.getError()
        );
    }
}
