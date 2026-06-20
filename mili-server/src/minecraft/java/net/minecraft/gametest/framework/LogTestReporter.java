package net.minecraft.gametest.framework;

import com.mojang.logging.LogUtils;
import net.minecraft.util.Util;
import org.slf4j.Logger;

public class LogTestReporter implements TestReporter {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onTestFailed(GameTestInfo testInfo) {
        String string = testInfo.getTestBlockPos().toShortString();
        if (testInfo.isRequired()) {
            LOGGER.error("{} failed at {}! {}", testInfo.id(), string, Util.describeError(testInfo.getError()));
        } else {
            LOGGER.warn("(optional) {} failed at {}. {}", testInfo.id(), string, Util.describeError(testInfo.getError()));
        }
    }

    @Override
    public void onTestSuccess(GameTestInfo testInfo) {
    }
}
