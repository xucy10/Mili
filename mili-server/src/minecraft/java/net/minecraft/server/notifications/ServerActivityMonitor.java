package net.minecraft.server.notifications;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.Util;

public class ServerActivityMonitor {
    private final long minimumMillisBetweenNotifications;
    private final AtomicLong lastNotificationTime = new AtomicLong();
    private final AtomicBoolean serverActivity = new AtomicBoolean(false);
    private final NotificationManager notificationManager;

    public ServerActivityMonitor(NotificationManager notificationManager, int minimumSecondsBetweenNotifications) {
        this.notificationManager = notificationManager;
        this.minimumMillisBetweenNotifications = TimeUnit.SECONDS.toMillis(minimumSecondsBetweenNotifications);
    }

    public void tick() {
        this.processWithRateLimit();
    }

    public void reportLoginActivity() {
        this.serverActivity.set(true);
        this.processWithRateLimit();
    }

    private void processWithRateLimit() {
        long millis = Util.getMillis();
        if (this.serverActivity.get() && millis - this.lastNotificationTime.get() >= this.minimumMillisBetweenNotifications) {
            this.notificationManager.serverActivityOccured();
            this.lastNotificationTime.set(Util.getMillis());
        }

        this.serverActivity.set(false);
    }
}
