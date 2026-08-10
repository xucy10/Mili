package net.minecraft.gametest.framework;

public interface GameTestListener {
    void testStructureLoaded(GameTestInfo testInfo);

    void testPassed(GameTestInfo test, GameTestRunner runner);

    void testFailed(GameTestInfo test, GameTestRunner runner);

    void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner);
}
