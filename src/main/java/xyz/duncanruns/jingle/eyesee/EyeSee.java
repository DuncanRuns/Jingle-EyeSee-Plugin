package xyz.duncanruns.jingle.eyesee;

import com.google.common.io.Resources;
import org.apache.logging.log4j.Level;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.JingleAppLaunch;
import xyz.duncanruns.jingle.eyesee.frames.EyeSeeFrame;
import xyz.duncanruns.jingle.eyesee.gui.EyeSeePluginPanel;
import xyz.duncanruns.jingle.gui.JingleGUI;
import xyz.duncanruns.jingle.plugin.PluginEvents;
import xyz.duncanruns.jingle.plugin.PluginManager;
import xyz.duncanruns.jingle.util.MonitorUtil;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class EyeSee {
    public static ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    private static EyeSeeFrame eyeSeeFrame = null;

    private static EyeSeeOptions options = null;

    public static EyeSeeOptions getOptions() {
        return options;
    }

    public static EyeSeeFrame getEyeSeeFrame() {
        return eyeSeeFrame;
    }

    public static void main(String[] args) throws IOException {
        JingleAppLaunch.launchWithDevPlugin(args, PluginManager.JinglePluginData.fromString(
                Resources.toString(Resources.getResource(EyeSee.class, "/jingle.plugin.json"), Charset.defaultCharset())
        ), EyeSee::initialize);
    }

    public static void initialize() {
        Optional<EyeSeeOptions> loadedOptions = EyeSeeOptions.load();
        if (loadedOptions.isPresent()) {
            options = loadedOptions.get();
        } else {
            options = new EyeSeeOptions();
            Jingle.log(Level.ERROR, "Failed to load EyeSeeOptions, using defaults.");
        }

        eyeSeeFrame = new EyeSeeFrame();

        EyeSeePluginPanel gui = new EyeSeePluginPanel();
        JingleGUI.addPluginTab("EyeSee", gui.mainPanel, gui::onSwitchTo);

        PluginEvents.SHOW_PROJECTOR.register(EyeSee::showProjector);
        PluginEvents.DUMP_PROJECTOR.register(EyeSee::dumpProjector);
        PluginEvents.STOP.register(EyeSee::stop);

        Jingle.log(Level.INFO, "EyeSee Plugin Initialized");
    }

    private static void stop() {
        EXECUTOR.shutdown();
        if (eyeSeeFrame != null) eyeSeeFrame.dispose();
        if (options != null) if (!options.trySave()) Jingle.log(Level.ERROR, "Failed to save EyeSee Options!");
    }

    private static void showProjector() {
        if (!isEnabled()) return;
        eyeSeeFrame.showEyeSee(getProjectorRect());
    }

    public static Rectangle getProjectorRect() {
        Rectangle rect;
        if (options.autoPos) {
            MonitorUtil.Monitor monitor = MonitorUtil.getPrimaryMonitor();
            int w = (monitor.pWidth - 384) / 2;
            int h = ((monitor.pHeight * w) / monitor.pWidth);
            rect = new Rectangle(0, (monitor.pHeight - h) / 2, w, h);
        } else {
            rect = new Rectangle(options.x, options.y, options.w, options.h);
        }
        return rect;
    }

    public static void dumpProjector() {
        if (!isEnabled()) return;
        eyeSeeFrame.hideEyeSee();
    }

    public static boolean isEnabled() {
        return options.enabled;
    }
}
