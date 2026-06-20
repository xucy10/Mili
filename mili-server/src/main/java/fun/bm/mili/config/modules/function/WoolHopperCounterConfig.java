package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.command.counter.CounterCommand;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "wool-hopper-counter")
public class WoolHopperCounterConfig implements IConfigModule {
    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;

    @ConfigInfo(name = "unlimited-speed")
    public static boolean unlimitedSpeed = false;

    @ConfigInfo(name = "tps-aware-throttle", comments = """
            If true, the wool hopper counter automatically reduces its transfer rate
            when the server TPS drops below 'tps-aware-threshold'. This prevents the
            counter from monopolising tick time during lag spikes - a critical
            stability improvement for large counters on busy servers.""")
    public static boolean tpsAwareThrottle = true;

    @ConfigInfo(name = "tps-aware-threshold", comments = """
            TPS threshold below which the wool hopper counter starts throttling itself.
            Only relevant when 'tps-aware-throttle' is true. Default 18.0 (5% below
            20 TPS) is a safe value for most servers.""")
    public static double tpsAwareThreshold = 18.0;

    @ConfigInfo(name = "max-transfers-per-tick", comments = """
            Cap on how many hopper transfers the wool hopper counter can perform per
            tick when 'unlimited-speed' is true. 0 = unlimited. Set this to a value
            like 256-1024 on busy technical servers to prevent tick time spikes.""")
    public static int maxTransfersPerTick = 0;

    @DoNotLoad
    private static CounterCommand counterCommand = null;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        if (enabled) {
            if (counterCommand == null) {
                counterCommand = new CounterCommand();
            }
            counterCommand.register();
        }
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        if (counterCommand != null) {
            counterCommand.unregister();
        }
    }
}
