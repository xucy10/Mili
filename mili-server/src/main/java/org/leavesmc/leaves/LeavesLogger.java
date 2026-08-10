package org.leavesmc.leaves;

import org.bukkit.Bukkit;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LeavesLogger extends Logger {
    public static final LeavesLogger LOGGER = new LeavesLogger();

    private LeavesLogger() {
        super("Leaves", null);
        setParentSafe();
        setLevel(Level.ALL);
    }

    private void setParentSafe() {
        try {
            setParent(Bukkit.getLogger());
        } catch (Throwable ignored) {
            // Bukkit not available yet
        }
    }

    public void severe(String msg, Exception exception) {
        this.log(Level.SEVERE, msg, exception);
    }

    public void warning(String msg, Exception exception) {
        this.log(Level.WARNING, msg, exception);
    }
}
