package net.minecraft.server.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.Timer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.TimeUtil;
import org.jspecify.annotations.Nullable;

public class StatsComponent extends JComponent {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("########0.000", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private final int[] values = new int[256];
    private int vp;
    private final @Nullable String[] msgs = new String[11];
    private final MinecraftServer server;
    private final Timer timer;

    public StatsComponent(MinecraftServer server) {
        this.server = server;
        this.setPreferredSize(new Dimension(456, 246));
        this.setMinimumSize(new Dimension(456, 246));
        this.setMaximumSize(new Dimension(456, 246));
        this.timer = new Timer(500, actionEvent -> this.tick());
        this.timer.start();
        this.setBackground(Color.BLACK);
    }

    private void tick() {
        long l = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        // Paper start - Improve ServerGUI
        double[] tps = org.bukkit.Bukkit.getTPS();
        String[] tpsAvg = new String[tps.length];

        for (int g = 0; g < tps.length; g++) {
            tpsAvg[g] = format(tps[g]);
        }
        this.msgs[0] = "Memory use: " + l / 1024L / 1024L + " mb (" + Runtime.getRuntime().freeMemory() * 100L / Runtime.getRuntime().maxMemory() + "% free)";
        this.msgs[1] = "Avg tick: " + DECIMAL_FORMAT.format((double)this.server.getAverageTickTimeNanos() / TimeUtil.NANOSECONDS_PER_MILLISECOND) + " ms";
        this.msgs[2] = "TPS from last 1m, 5m, 15m: " + String.join(", ", tpsAvg);
        // Paper end - Improve ServerGUI
        this.values[this.vp++ & 0xFF] = (int)(l * 100L / Runtime.getRuntime().maxMemory());
        this.repaint();
    }

    @Override
    public void paint(Graphics graphics) {
        graphics.setColor(new Color(16777215));
        graphics.fillRect(0, 0, 456, 246);

        for (int i = 0; i < 256; i++) {
            int i1 = this.values[i + this.vp & 0xFF];
            graphics.setColor(new Color(i1 + 28 << 16));
            graphics.fillRect(i, 100 - i1, 1, i1);
        }

        graphics.setColor(Color.BLACK);

        for (int i = 0; i < this.msgs.length; i++) {
            String string = this.msgs[i];
            if (string != null) {
                graphics.drawString(string, 32, 116 + i * 16);
            }
        }
    }

    public void close() {
        this.timer.stop();
    }

    // Paper start - Improve ServerGUI
    private static String format(double tps) {
        return (( tps > 21.0 ) ? "*" : "") + Math.min(Math.round(tps * 100.0) / 100.0, 20.0); // only print * at 21, we commonly peak to 20.02 as the tick sleep is not accurate enough, stop the noise
    }
    // Paper end - Improve ServerGUI
}
