package net.minecraft.gametest.framework;

public record RetryOptions(int numberOfTries, boolean haltOnFailure) {
    private static final RetryOptions NO_RETRIES = new RetryOptions(1, true);

    public static RetryOptions noRetries() {
        return NO_RETRIES;
    }

    public boolean unlimitedTries() {
        return this.numberOfTries < 1;
    }

    public boolean hasTriesLeft(int attempts, int successes) {
        boolean flag = attempts != successes;
        boolean flag1 = this.unlimitedTries() || attempts < this.numberOfTries;
        return flag1 && (!flag || !this.haltOnFailure);
    }

    public boolean hasRetries() {
        return this.numberOfTries != 1;
    }
}
