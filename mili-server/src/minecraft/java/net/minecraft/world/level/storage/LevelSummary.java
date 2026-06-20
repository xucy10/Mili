package net.minecraft.world.level.storage;

import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public class LevelSummary implements Comparable<LevelSummary> {
    public static final Component PLAY_WORLD = Component.translatable("selectWorld.select");
    private final LevelSettings settings;
    private final LevelVersion levelVersion;
    private final String levelId;
    private final boolean requiresManualConversion;
    private final boolean locked;
    private final boolean experimental;
    private final Path icon;
    private @Nullable Component info;

    public LevelSummary(
        LevelSettings settings, LevelVersion levelVersion, String levelId, boolean requiresManualConversion, boolean locked, boolean experimental, Path icon
    ) {
        this.settings = settings;
        this.levelVersion = levelVersion;
        this.levelId = levelId;
        this.locked = locked;
        this.experimental = experimental;
        this.icon = icon;
        this.requiresManualConversion = requiresManualConversion;
    }

    public String getLevelId() {
        return this.levelId;
    }

    public String getLevelName() {
        return StringUtils.isEmpty(this.settings.levelName()) ? this.levelId : this.settings.levelName();
    }

    public Path getIcon() {
        return this.icon;
    }

    public boolean requiresManualConversion() {
        return this.requiresManualConversion;
    }

    public boolean isExperimental() {
        return this.experimental;
    }

    public long getLastPlayed() {
        return this.levelVersion.lastPlayed();
    }

    @Override
    public int compareTo(LevelSummary other) {
        if (this.getLastPlayed() < other.getLastPlayed()) {
            return 1;
        } else {
            return this.getLastPlayed() > other.getLastPlayed() ? -1 : this.levelId.compareTo(other.levelId);
        }
    }

    public LevelSettings getSettings() {
        return this.settings;
    }

    public GameType getGameMode() {
        return this.settings.gameType();
    }

    public boolean isHardcore() {
        return this.settings.hardcore();
    }

    public boolean hasCommands() {
        return this.settings.allowCommands();
    }

    public MutableComponent getWorldVersionName() {
        return StringUtil.isNullOrEmpty(this.levelVersion.minecraftVersionName())
            ? Component.translatable("selectWorld.versionUnknown")
            : Component.literal(this.levelVersion.minecraftVersionName());
    }

    public LevelVersion levelVersion() {
        return this.levelVersion;
    }

    public boolean shouldBackup() {
        return this.backupStatus().shouldBackup();
    }

    public boolean isDowngrade() {
        return this.backupStatus() == LevelSummary.BackupStatus.DOWNGRADE;
    }

    public LevelSummary.BackupStatus backupStatus() {
        WorldVersion currentVersion = SharedConstants.getCurrentVersion();
        int version = currentVersion.dataVersion().version();
        int version1 = this.levelVersion.minecraftVersion().version();
        if (!currentVersion.stable() && version1 < version) {
            return LevelSummary.BackupStatus.UPGRADE_TO_SNAPSHOT;
        } else {
            return version1 > version ? LevelSummary.BackupStatus.DOWNGRADE : LevelSummary.BackupStatus.NONE;
        }
    }

    public boolean isLocked() {
        return this.locked;
    }

    public boolean isDisabled() {
        return this.isLocked() || this.requiresManualConversion() || !this.isCompatible();
    }

    public boolean isCompatible() {
        return SharedConstants.getCurrentVersion().dataVersion().isCompatible(this.levelVersion.minecraftVersion());
    }

    public Component getInfo() {
        if (this.info == null) {
            this.info = this.createInfo();
        }

        return this.info;
    }

    private Component createInfo() {
        if (this.isLocked()) {
            return Component.translatable("selectWorld.locked").withStyle(ChatFormatting.RED);
        } else if (this.requiresManualConversion()) {
            return Component.translatable("selectWorld.conversion").withStyle(ChatFormatting.RED);
        } else if (!this.isCompatible()) {
            return Component.translatable("selectWorld.incompatible.info", this.getWorldVersionName()).withStyle(ChatFormatting.RED);
        } else {
            MutableComponent mutableComponent = this.isHardcore()
                ? Component.empty().append(Component.translatable("gameMode.hardcore").withColor(-65536))
                : Component.translatable("gameMode." + this.getGameMode().getName());
            if (this.hasCommands()) {
                mutableComponent.append(", ").append(Component.translatable("selectWorld.commands"));
            }

            if (this.isExperimental()) {
                mutableComponent.append(", ").append(Component.translatable("selectWorld.experimental").withStyle(ChatFormatting.YELLOW));
            }

            MutableComponent worldVersionName = this.getWorldVersionName();
            MutableComponent mutableComponent1 = Component.literal(", ").append(Component.translatable("selectWorld.version")).append(CommonComponents.SPACE);
            if (this.shouldBackup()) {
                mutableComponent1.append(worldVersionName.withStyle(this.isDowngrade() ? ChatFormatting.RED : ChatFormatting.ITALIC));
            } else {
                mutableComponent1.append(worldVersionName);
            }

            mutableComponent.append(mutableComponent1);
            return mutableComponent;
        }
    }

    public Component primaryActionMessage() {
        return PLAY_WORLD;
    }

    public boolean primaryActionActive() {
        return !this.isDisabled();
    }

    public boolean canUpload() {
        return !this.requiresManualConversion() && !this.isLocked();
    }

    public boolean canEdit() {
        return !this.isDisabled();
    }

    public boolean canRecreate() {
        return !this.isDisabled();
    }

    public boolean canDelete() {
        return true;
    }

    public static enum BackupStatus {
        NONE(false, false, ""),
        DOWNGRADE(true, true, "downgrade"),
        UPGRADE_TO_SNAPSHOT(true, false, "snapshot");

        private final boolean shouldBackup;
        private final boolean severe;
        private final String translationKey;

        private BackupStatus(final boolean shouldBackup, final boolean severe, final String translationKey) {
            this.shouldBackup = shouldBackup;
            this.severe = severe;
            this.translationKey = translationKey;
        }

        public boolean shouldBackup() {
            return this.shouldBackup;
        }

        public boolean isSevere() {
            return this.severe;
        }

        public String getTranslationKey() {
            return this.translationKey;
        }
    }

    public static class CorruptedLevelSummary extends LevelSummary {
        private static final Component INFO = Component.translatable("recover_world.warning").withStyle(style -> style.withColor(-65536));
        private static final Component RECOVER = Component.translatable("recover_world.button");
        private final long lastPlayed;

        public CorruptedLevelSummary(String levelId, Path icon, long lastPlayed) {
            super(null, null, levelId, false, false, false, icon);
            this.lastPlayed = lastPlayed;
        }

        @Override
        public String getLevelName() {
            return this.getLevelId();
        }

        @Override
        public Component getInfo() {
            return INFO;
        }

        @Override
        public long getLastPlayed() {
            return this.lastPlayed;
        }

        @Override
        public boolean isDisabled() {
            return false;
        }

        @Override
        public Component primaryActionMessage() {
            return RECOVER;
        }

        @Override
        public boolean primaryActionActive() {
            return true;
        }

        @Override
        public boolean canUpload() {
            return false;
        }

        @Override
        public boolean canEdit() {
            return false;
        }

        @Override
        public boolean canRecreate() {
            return false;
        }
    }

    public static class SymlinkLevelSummary extends LevelSummary {
        private static final Component MORE_INFO_BUTTON = Component.translatable("symlink_warning.more_info");
        private static final Component INFO = Component.translatable("symlink_warning.title").withColor(-65536);

        public SymlinkLevelSummary(String levelId, Path icon) {
            super(null, null, levelId, false, false, false, icon);
        }

        @Override
        public String getLevelName() {
            return this.getLevelId();
        }

        @Override
        public Component getInfo() {
            return INFO;
        }

        @Override
        public long getLastPlayed() {
            return -1L;
        }

        @Override
        public boolean isDisabled() {
            return false;
        }

        @Override
        public Component primaryActionMessage() {
            return MORE_INFO_BUTTON;
        }

        @Override
        public boolean primaryActionActive() {
            return true;
        }

        @Override
        public boolean canUpload() {
            return false;
        }

        @Override
        public boolean canEdit() {
            return false;
        }

        @Override
        public boolean canRecreate() {
            return false;
        }
    }
}
