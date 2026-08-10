package net.minecraft.gametest.framework;

public class GlobalTestReporter {
    private static TestReporter DELEGATE = new LogTestReporter();

    public static void replaceWith(TestReporter testReporter) {
        DELEGATE = testReporter;
    }

    public static void onTestFailed(GameTestInfo testInfo) {
        DELEGATE.onTestFailed(testInfo);
    }

    public static void onTestSuccess(GameTestInfo testInfo) {
        DELEGATE.onTestSuccess(testInfo);
    }

    public static void finish() {
        DELEGATE.finish();
    }
}
