package net.minecraft.server.gui;

import com.google.common.collect.Lists;
import com.mojang.logging.LogQueues;
import com.mojang.logging.LogUtils;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;

public class MinecraftServerGui extends JComponent {
    private static final Font MONOSPACED = new Font("Monospaced", 0, 12);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TITLE = "Minecraft server";
    private static final String SHUTDOWN_TITLE = "Minecraft server - shutting down!";
    private final DedicatedServer server;
    private Thread logAppenderThread;
    private final Collection<Runnable> finalizers = Lists.newArrayList();
    final AtomicBoolean isClosing = new AtomicBoolean();

    public static MinecraftServerGui showFrameFor(final DedicatedServer server) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception var3) {
        }

        final JFrame jFrame = new JFrame("Minecraft server");
        final MinecraftServerGui minecraftServerGui = new MinecraftServerGui(server);
        jFrame.setDefaultCloseOperation(2);
        jFrame.add(minecraftServerGui);
        jFrame.pack();
        jFrame.setLocationRelativeTo(null);
        jFrame.setVisible(true);
        // Paper start - Improve ServerGUI
        jFrame.setName("Minecraft server");
        try {
            jFrame.setIconImage(javax.imageio.ImageIO.read(java.util.Objects.requireNonNull(MinecraftServerGui.class.getClassLoader().getResourceAsStream("logo.png"))));
        } catch (java.io.IOException ignore) {
        }
        // Paper end - Improve ServerGUI
        jFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (!minecraftServerGui.isClosing.getAndSet(true)) {
                    jFrame.setTitle("Minecraft server - shutting down!");
                    server.halt(true);
                    minecraftServerGui.runFinalizers();
                }
            }
        });
        minecraftServerGui.addFinalizer(jFrame::dispose);
        minecraftServerGui.start();
        return minecraftServerGui;
    }

    private MinecraftServerGui(DedicatedServer server) {
        this.server = server;
        this.setPreferredSize(new Dimension(854, 480));
        this.setLayout(new BorderLayout());

        try {
            this.add(this.buildOnboardingPanel(), "North"); // Paper - Add onboarding message for initial server start
            this.add(this.buildChatPanel(), "Center");
            this.add(this.buildInfoPanel(), "West");
        } catch (Exception var3) {
            LOGGER.error("Couldn't build server GUI", (Throwable)var3);
        }
    }

    public void addFinalizer(Runnable finalizer) {
        this.finalizers.add(finalizer);
    }

    private JComponent buildInfoPanel() {
        JPanel jPanel = new JPanel(new BorderLayout());
        com.destroystokyo.paper.gui.GuiStatsComponent statsComponent = new com.destroystokyo.paper.gui.GuiStatsComponent(this.server); // Paper - Make GUI graph fancier
        this.finalizers.add(statsComponent::close);
        jPanel.add(statsComponent, "North");
        jPanel.add(this.buildPlayerPanel(), "Center");
        jPanel.setBorder(new TitledBorder(new EtchedBorder(), "Stats"));
        return jPanel;
    }

    private JComponent buildPlayerPanel() {
        JList<?> jList = new PlayerListComponent(this.server);
        JScrollPane jScrollPane = new JScrollPane(jList, 22, 30);
        jScrollPane.setBorder(new TitledBorder(new EtchedBorder(), "Players"));
        return jScrollPane;
    }

    private JComponent buildChatPanel() {
        JPanel jPanel = new JPanel(new BorderLayout());
        JTextArea jTextArea = new JTextArea();
        JScrollPane jScrollPane = new JScrollPane(jTextArea, 22, 30);
        jTextArea.setEditable(false);
        jTextArea.setFont(MONOSPACED);
        JTextField jTextField = new JTextField();
        jTextField.addActionListener(actionEvent -> {
            String trimmed = jTextField.getText().trim();
            if (!trimmed.isEmpty()) {
                this.server.handleConsoleInput(trimmed, this.server.createCommandSourceStack());
            }

            jTextField.setText("");
        });
        jTextArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
            }
        });
        jPanel.add(jScrollPane, "Center");
        jPanel.add(jTextField, "South");
        jPanel.setBorder(new TitledBorder(new EtchedBorder(), "Log and chat"));
        this.logAppenderThread = new Thread(() -> {
            String string;
            while ((string = LogQueues.getNextLogEvent("ServerGuiConsole")) != null) {
                this.print(jTextArea, jScrollPane, string);
            }
        });
        this.logAppenderThread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        this.logAppenderThread.setDaemon(true);
        return jPanel;
    }

    public void start() {
        this.logAppenderThread.start();
    }

    public void close() {
        if (!this.isClosing.getAndSet(true)) {
            this.runFinalizers();
        }
    }

    void runFinalizers() {
        this.finalizers.forEach(Runnable::run);
    }

    private static final java.util.regex.Pattern ANSI = java.util.regex.Pattern.compile("\\e\\[[\\d;]*[^\\d;]"); // CraftBukkit // Paper
    public void print(JTextArea textArea, JScrollPane scrollPane, String line) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> this.print(textArea, scrollPane, line));
        } else {
            Document document = textArea.getDocument();
            JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
            boolean flag = false;
            if (scrollPane.getViewport().getView() == textArea) {
                flag = verticalScrollBar.getValue() + verticalScrollBar.getSize().getHeight() + MONOSPACED.getSize() * 4 > verticalScrollBar.getMaximum();
            }

            try {
                document.insertString(document.getLength(), MinecraftServerGui.ANSI.matcher(line).replaceAll(""), null); // CraftBukkit
            } catch (BadLocationException var8) {
            }

            if (flag) {
                verticalScrollBar.setValue(Integer.MAX_VALUE);
            }
        }
    }

    // Paper start - Add onboarding message for initial server start
    private JComponent buildOnboardingPanel() {
        String onboardingLink = "https://docs.papermc.io/paper/next-steps";
        JPanel jPanel = new JPanel();

        javax.swing.JLabel jLabel = new javax.swing.JLabel("If you need help setting up your server you can visit:");
        jLabel.setFont(MinecraftServerGui.MONOSPACED);

        javax.swing.JLabel link = new javax.swing.JLabel("<html><u> " + onboardingLink + "</u></html>");
        link.setFont(MinecraftServerGui.MONOSPACED);
        link.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        link.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(final java.awt.event.MouseEvent e) {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(onboardingLink));
                } catch (java.io.IOException exception) {
                    LOGGER.error("Unable to find a default browser. Please manually visit the website: " + onboardingLink, exception);
                } catch (UnsupportedOperationException exception) {
                    LOGGER.error("This platform does not support the BROWSE action. Please manually visit the website: " + onboardingLink, exception);
                } catch (SecurityException exception) {
                    LOGGER.error("This action has been denied by the security manager. Please manually visit the website: " + onboardingLink, exception);
                }
            }
        });

        jPanel.add(jLabel);
        jPanel.add(link);

        return jPanel;
    }
    // Paper end - Add onboarding message for initial server start
}
