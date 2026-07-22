package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.command.BackupCommand;
import fun.bm.mili.utils.AutoBackupManager;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "auto-backup")
public class AutoBackupConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用自动备份功能""")
    public static boolean enabled = true;

    @ConfigInfo(name = "interval-minutes", comments = """
            备份间隔（分钟）""")
    public static int intervalMinutes = 60;

    @ConfigInfo(name = "max-backups", comments = """
            保留的最大备份数量""")
    public static int maxBackups = 5;

    @ConfigInfo(name = "backup-path", comments = """
            备份文件存储路径""")
    public static String backupPath = "backups/";

    @ConfigInfo(name = "backup-worlds", comments = """
            要备份的世界列表（空列表 = 全部世界）""")
    public static java.util.List<String> backupWorlds = java.util.List.of();

    @ConfigInfo(name = "compress", comments = """
            是否压缩备份""")
    public static boolean compress = true;

    @ConfigInfo(name = "save-on-backup", comments = """
            备份前是否先保存世界""")
    public static boolean saveOnBackup = true;

    @ConfigInfo(name = "notify-players", comments = """
            备份时通知在线玩家""")
    public static boolean notifyPlayers = true;

    @DoNotLoad
    private static BackupCommand command = null;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        if (command == null) {
            command = new BackupCommand();
        }
        command.register();
        if (enabled) {
            AutoBackupManager.start();
        }
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        AutoBackupManager.stop();
        if (command != null) {
            command.unregister();
        }
    }
}
