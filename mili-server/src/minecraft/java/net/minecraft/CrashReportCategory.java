package net.minecraft;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class CrashReportCategory {
    private final String title;
    private final List<CrashReportCategory.Entry> entries = Lists.newArrayList();
    private StackTraceElement[] stackTrace = new StackTraceElement[0];

    public CrashReportCategory(String title) {
        this.title = title;
    }

    public static String formatLocation(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", x, y, z);
    }

    public static String formatLocation(LevelHeightAccessor level, double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f - %s", x, y, z, formatLocation(level, BlockPos.containing(x, y, z)));
    }

    public static String formatLocation(LevelHeightAccessor level, BlockPos pos) {
        return formatLocation(level, pos.getX(), pos.getY(), pos.getZ());
    }

    public static String formatLocation(LevelHeightAccessor level, int x, int y, int z) {
        StringBuilder stringBuilder = new StringBuilder();

        try {
            stringBuilder.append(String.format(Locale.ROOT, "World: (%d,%d,%d)", x, y, z));
        } catch (Throwable var19) {
            stringBuilder.append("(Error finding world loc)");
        }

        stringBuilder.append(", ");

        try {
            int sectionPosX = SectionPos.blockToSectionCoord(x);
            int sectionPosY = SectionPos.blockToSectionCoord(y);
            int sectionPosZ = SectionPos.blockToSectionCoord(z);
            int i = x & 15;
            int i1 = y & 15;
            int i2 = z & 15;
            int blockPosX = SectionPos.sectionToBlockCoord(sectionPosX);
            int minY = level.getMinY();
            int blockPosZ = SectionPos.sectionToBlockCoord(sectionPosZ);
            int i3 = SectionPos.sectionToBlockCoord(sectionPosX + 1) - 1;
            int maxY = level.getMaxY();
            int i4 = SectionPos.sectionToBlockCoord(sectionPosZ + 1) - 1;
            stringBuilder.append(
                String.format(
                    Locale.ROOT,
                    "Section: (at %d,%d,%d in %d,%d,%d; chunk contains blocks %d,%d,%d to %d,%d,%d)",
                    i,
                    i1,
                    i2,
                    sectionPosX,
                    sectionPosY,
                    sectionPosZ,
                    blockPosX,
                    minY,
                    blockPosZ,
                    i3,
                    maxY,
                    i4
                )
            );
        } catch (Throwable var18) {
            stringBuilder.append("(Error finding chunk loc)");
        }

        stringBuilder.append(", ");

        try {
            int sectionPosX = x >> 9;
            int sectionPosY = z >> 9;
            int sectionPosZ = sectionPosX << 5;
            int i = sectionPosY << 5;
            int i1 = (sectionPosX + 1 << 5) - 1;
            int i2 = (sectionPosY + 1 << 5) - 1;
            int blockPosX = sectionPosX << 9;
            int minY = level.getMinY();
            int blockPosZ = sectionPosY << 9;
            int i3 = (sectionPosX + 1 << 9) - 1;
            int maxY = level.getMaxY();
            int i4 = (sectionPosY + 1 << 9) - 1;
            stringBuilder.append(
                String.format(
                    Locale.ROOT,
                    "Region: (%d,%d; contains chunks %d,%d to %d,%d, blocks %d,%d,%d to %d,%d,%d)",
                    sectionPosX,
                    sectionPosY,
                    sectionPosZ,
                    i,
                    i1,
                    i2,
                    blockPosX,
                    minY,
                    blockPosZ,
                    i3,
                    maxY,
                    i4
                )
            );
        } catch (Throwable var17) {
            stringBuilder.append("(Error finding world loc)");
        }

        return stringBuilder.toString();
    }

    public CrashReportCategory setDetail(String name, CrashReportDetail<String> detail) {
        try {
            this.setDetail(name, detail.call());
        } catch (Throwable var4) {
            this.setDetailError(name, var4);
        }

        return this;
    }

    public CrashReportCategory setDetail(String sectionName, Object value) {
        this.entries.add(new CrashReportCategory.Entry(sectionName, value));
        return this;
    }

    public void setDetailError(String sectionName, Throwable throwable) {
        this.setDetail(sectionName, throwable);
    }

    public int fillInStackTrace(int size) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length <= 0) {
            return 0;
        } else {
            this.stackTrace = new StackTraceElement[stackTrace.length - 3 - size];
            System.arraycopy(stackTrace, 3 + size, this.stackTrace, 0, this.stackTrace.length);
            return this.stackTrace.length;
        }
    }

    public boolean validateStackTrace(StackTraceElement s1, StackTraceElement s2) {
        if (this.stackTrace.length != 0 && s1 != null) {
            StackTraceElement stackTraceElement = this.stackTrace[0];
            if (stackTraceElement.isNativeMethod() == s1.isNativeMethod()
                && stackTraceElement.getClassName().equals(s1.getClassName())
                && stackTraceElement.getFileName().equals(s1.getFileName())
                && stackTraceElement.getMethodName().equals(s1.getMethodName())) {
                if (s2 != null != this.stackTrace.length > 1) {
                    return false;
                } else if (s2 != null && !this.stackTrace[1].equals(s2)) {
                    return false;
                } else {
                    this.stackTrace[0] = s1;
                    return true;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void trimStacktrace(int amount) {
        StackTraceElement[] stackTraceElements = new StackTraceElement[this.stackTrace.length - amount];
        System.arraycopy(this.stackTrace, 0, stackTraceElements, 0, stackTraceElements.length);
        this.stackTrace = stackTraceElements;
    }

    public void getDetails(StringBuilder builder) {
        builder.append("-- ").append(this.title).append(" --\n");
        builder.append("Details:");

        for (CrashReportCategory.Entry entry : this.entries) {
            builder.append("\n\t");
            builder.append(entry.getKey());
            builder.append(": ");
            builder.append(entry.getValue());
        }

        if (this.stackTrace != null && this.stackTrace.length > 0) {
            builder.append("\nStacktrace:");

            for (StackTraceElement stackTraceElement : this.stackTrace) {
                builder.append("\n\tat ");
                builder.append(stackTraceElement);
            }
        }
    }

    public StackTraceElement[] getStacktrace() {
        return this.stackTrace;
    }

    public static void populateBlockDetails(CrashReportCategory category, LevelHeightAccessor level, BlockPos pos, BlockState state) {
        category.setDetail("Block", state::toString);
        populateBlockLocationDetails(category, level, pos);
    }

    public static CrashReportCategory populateBlockLocationDetails(CrashReportCategory category, LevelHeightAccessor level, BlockPos pos) {
        return category.setDetail("Block location", () -> formatLocation(level, pos));
    }

    static class Entry {
        private final String key;
        private final String value;

        public Entry(String key, @Nullable Object value) {
            this.key = key;
            if (value == null) {
                this.value = "~~NULL~~";
            } else if (value instanceof Throwable throwable) {
                this.value = "~~ERROR~~ " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            } else {
                this.value = value.toString();
            }
        }

        public String getKey() {
            return this.key;
        }

        public String getValue() {
            return this.value;
        }
    }
}
