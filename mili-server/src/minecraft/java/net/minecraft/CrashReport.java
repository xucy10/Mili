package net.minecraft;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import net.minecraft.util.FileUtil;
import net.minecraft.util.MemoryReserve;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CrashReport {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private final String title;
    private final Throwable exception;
    private final List<CrashReportCategory> details = Lists.newArrayList();
    private @Nullable Path saveFile;
    private boolean trackingStackTrace = true;
    private StackTraceElement[] uncategorizedStackTrace = new StackTraceElement[0];
    private final SystemReport systemReport = new SystemReport();

    public CrashReport(String title, Throwable exception) {
        this.title = title;
        this.exception = exception;
        this.systemReport.setDetail("CraftBukkit Information", new org.bukkit.craftbukkit.CraftCrashReport()); // CraftBukkit
    }

    public String getTitle() {
        return this.title;
    }

    public Throwable getException() {
        return this.exception;
    }

    public String getDetails() {
        StringBuilder stringBuilder = new StringBuilder();
        this.getDetails(stringBuilder);
        return stringBuilder.toString();
    }

    public void getDetails(StringBuilder builder) {
        if ((this.uncategorizedStackTrace == null || this.uncategorizedStackTrace.length <= 0) && !this.details.isEmpty()) {
            this.uncategorizedStackTrace = ArrayUtils.subarray(this.details.get(0).getStacktrace(), 0, 1);
        }

        if (this.uncategorizedStackTrace != null && this.uncategorizedStackTrace.length > 0) {
            builder.append("-- Head --\n");
            builder.append("Thread: ").append(Thread.currentThread().getName()).append("\n");
            builder.append("Stacktrace:\n");

            for (StackTraceElement stackTraceElement : this.uncategorizedStackTrace) {
                builder.append("\t").append("at ").append(stackTraceElement);
                builder.append("\n");
            }

            builder.append("\n");
        }

        for (CrashReportCategory crashReportCategory : this.details) {
            crashReportCategory.getDetails(builder);
            builder.append("\n\n");
        }

        this.systemReport.appendToCrashReportString(builder);
    }

    public String getExceptionMessage() {
        StringWriter stringWriter = null;
        PrintWriter printWriter = null;
        Throwable throwable = this.exception;
        if (throwable.getMessage() == null) {
            if (throwable instanceof NullPointerException) {
                throwable = new NullPointerException(this.title);
            } else if (throwable instanceof StackOverflowError) {
                throwable = new StackOverflowError(this.title);
            } else if (throwable instanceof OutOfMemoryError) {
                throwable = new OutOfMemoryError(this.title);
            }

            throwable.setStackTrace(this.exception.getStackTrace());
        }

        String var4;
        try {
            stringWriter = new StringWriter();
            printWriter = new PrintWriter(stringWriter);
            throwable.printStackTrace(printWriter);
            var4 = stringWriter.toString();
        } finally {
            IOUtils.closeQuietly((Writer)stringWriter);
            IOUtils.closeQuietly((Writer)printWriter);
        }

        return var4;
    }

    public String getFriendlyReport(ReportType type, List<String> links) {
        StringBuilder stringBuilder = new StringBuilder();
        type.appendHeader(stringBuilder, links);
        stringBuilder.append("Time: ");
        stringBuilder.append(DATE_TIME_FORMATTER.format(ZonedDateTime.now()));
        stringBuilder.append("\n");
        stringBuilder.append("Description: ");
        stringBuilder.append(this.title);
        stringBuilder.append("\n\n");
        stringBuilder.append(this.getExceptionMessage());
        stringBuilder.append("\n\nA detailed walkthrough of the error, its code path and all known details is as follows:\n");

        for (int i = 0; i < 87; i++) {
            stringBuilder.append("-");
        }

        stringBuilder.append("\n\n");
        this.getDetails(stringBuilder);
        return stringBuilder.toString();
    }

    public String getFriendlyReport(ReportType type) {
        return this.getFriendlyReport(type, List.of());
    }

    public @Nullable Path getSaveFile() {
        return this.saveFile;
    }

    public boolean saveToFile(Path path, ReportType type, List<String> links) {
        if (this.saveFile != null) {
            return false;
        } else {
            try {
                if (path.getParent() != null) {
                    FileUtil.createDirectoriesSafe(path.getParent());
                }

                try (Writer bufferedWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    bufferedWriter.write(this.getFriendlyReport(type, links));
                }

                this.saveFile = path;
                return true;
            } catch (Throwable var9) {
                LOGGER.error("Could not save crash report to {}", path, var9);
                return false;
            }
        }
    }

    public boolean saveToFile(Path path, ReportType type) {
        return this.saveToFile(path, type, List.of());
    }

    public SystemReport getSystemReport() {
        return this.systemReport;
    }

    public CrashReportCategory addCategory(String name) {
        return this.addCategory(name, 1);
    }

    public CrashReportCategory addCategory(String categoryName, int stacktraceLength) {
        CrashReportCategory crashReportCategory = new CrashReportCategory(categoryName);
        if (this.trackingStackTrace) {
            int i = crashReportCategory.fillInStackTrace(stacktraceLength);
            StackTraceElement[] stackTrace = this.exception.getStackTrace();
            StackTraceElement stackTraceElement = null;
            StackTraceElement stackTraceElement1 = null;
            int i1 = stackTrace.length - i;
            if (i1 < 0) {
                LOGGER.error("Negative index in crash report handler ({}/{})", stackTrace.length, i);
            }

            if (stackTrace != null && 0 <= i1 && i1 < stackTrace.length) {
                stackTraceElement = stackTrace[i1];
                if (stackTrace.length + 1 - i < stackTrace.length) {
                    stackTraceElement1 = stackTrace[stackTrace.length + 1 - i];
                }
            }

            this.trackingStackTrace = crashReportCategory.validateStackTrace(stackTraceElement, stackTraceElement1);
            if (stackTrace != null && stackTrace.length >= i && 0 <= i1 && i1 < stackTrace.length) {
                this.uncategorizedStackTrace = new StackTraceElement[i1];
                System.arraycopy(stackTrace, 0, this.uncategorizedStackTrace, 0, this.uncategorizedStackTrace.length);
            } else {
                this.trackingStackTrace = false;
            }
        }

        this.details.add(crashReportCategory);
        return crashReportCategory;
    }

    public static CrashReport forThrowable(Throwable cause, String description) {
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }

        CrashReport report;
        if (cause instanceof ReportedException reportedException) {
            report = reportedException.getReport();
        } else {
            report = new CrashReport(description, cause);
        }

        return report;
    }

    public static void preload() {
        //MemoryReserve.allocate(); // Paper - Disable memory reserve allocating
        new CrashReport("Don't panic!", new Throwable()).getFriendlyReport(ReportType.CRASH);
    }
}
