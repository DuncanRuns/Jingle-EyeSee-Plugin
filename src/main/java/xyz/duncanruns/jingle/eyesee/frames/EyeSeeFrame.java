package xyz.duncanruns.jingle.eyesee.frames;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import org.apache.logging.log4j.Level;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.eyesee.EyeSee;
import xyz.duncanruns.jingle.eyesee.win32.GDI32Extra;
import xyz.duncanruns.jingle.util.WindowStateUtil;
import xyz.duncanruns.jingle.win32.User32;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author DuncanRuns, draconix6
 */
public class EyeSeeFrame extends JFrame {
    private static final int SHOW_FLAGS = User32.SWP_NOACTIVATE | User32.SWP_NOSENDCHANGING;
    private static final WinDef.DWORD SRCCOPY = new WinDef.DWORD(0x00CC0020);
    private final OverlayFrame overlay = new OverlayFrame();
    private ScheduledFuture<?> redrawTask;

    private final WinDef.HWND eyeSeeHwnd;
    private boolean currentlyShowing = false;
    private Rectangle bounds = new Rectangle();

    private Dimension prevWindowSize = null;

    public EyeSeeFrame() {
        super();
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                Jingle.log(Level.DEBUG, "EyeSee Closed.");
            }
        });
        this.setResizable(false);
        this.setTitle("Jingle EyeSee");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setAlwaysOnTop(true);
        this.overlay.setAlwaysOnTop(true);

        // set borderless
        this.setVisible(true);
        eyeSeeHwnd = new WinDef.HWND(Native.getWindowPointer(this));
        WindowStateUtil.setHwndBorderless(eyeSeeHwnd);

        tick();
        restartDrawingTask(EyeSee.getOptions().fpsLimit);
        this.setVisible(false);

//        this.showEyeSee();
    }

    private void tick() {
        if (!currentlyShowing) return;
        if (!Jingle.getMainInstance().isPresent()) return;
        WinDef.HWND hwnd = Jingle.getMainInstance().get().hwnd;

        // get snapshot of MC window
        Rectangle rectangle = getYoinkArea(hwnd);
        WinDef.HDC sourceHDC = User32.INSTANCE.GetDC(hwnd);
        WinDef.HDC eyeSeeHDC = User32.INSTANCE.GetDC(eyeSeeHwnd);

        // render MC window to EyeSee window
        GDI32Extra.INSTANCE.SetStretchBltMode(eyeSeeHDC, 3);
        GDI32Extra.INSTANCE.StretchBlt(eyeSeeHDC, 0, 0, bounds.width, bounds.height, sourceHDC, rectangle.x, rectangle.y, rectangle.width, rectangle.height, SRCCOPY);

        User32.INSTANCE.ReleaseDC(hwnd, sourceHDC);
        User32.INSTANCE.ReleaseDC(eyeSeeHwnd, eyeSeeHDC);
    }

    public void showEyeSee(Rectangle rect) {
        if (!Jingle.getMainInstance().isPresent()) return;
        Jingle.log(Level.DEBUG, "Showing EyeSee...");

        currentlyShowing = true;
        this.setFocusableWindowState(false);
        this.setVisible(true);
        this.overlay.setFocusableWindowState(false);
        this.overlay.setVisible(true);
        bounds = rect;

        // move eyesee window
        User32.INSTANCE.SetWindowPos(
                eyeSeeHwnd,
                new WinDef.HWND(new Pointer(0)),
                rect.x,
                rect.y,
                rect.width,
                rect.height,
                SHOW_FLAGS
        );

        // add overlay image & resize accordingly
        Dimension currentSize = new Dimension(rect.width, rect.height);
        if (!Objects.equals(prevWindowSize, currentSize)) {
            prevWindowSize = currentSize;

            Image image = this.overlay.icon.getImage();
            // undo system scaling
            AffineTransform transform = getGraphicsConfiguration().getDefaultTransform();
            double scaleX = transform.getScaleX();
            double scaleY = transform.getScaleY();
            Image newImage = image.getScaledInstance((int) (currentSize.width / scaleX), (int) (currentSize.height / scaleY), Image.SCALE_SMOOTH);
            this.overlay.icon = new ImageIcon(newImage);
            this.overlay.label = new JLabel(this.overlay.icon);
            this.overlay.add(this.overlay.label);
        }

        // move overlay window
        User32.INSTANCE.SetWindowPos(
                this.overlay.hwnd,
                new WinDef.HWND(new Pointer(0)),
                rect.x,
                rect.y,
                rect.width,
                rect.height,
                SHOW_FLAGS
        );
//        this.overlay.setSize(projectorWidth, projectorHeight);
//        this.overlay.setLocation(projectorXPos, projectorYPos);
//        User32.INSTANCE.BringWindowToTop(this.overlay.hwnd);
    }

    public void hideEyeSee() {
        Jingle.log(Level.DEBUG, "Hiding EyeSee...");
        currentlyShowing = false;
        this.setVisible(false);
        this.overlay.setVisible(false);

//        MonitorUtil.Monitor monitor = MonitorUtil.getPrimaryMonitor();
//        User32.INSTANCE.SetWindowPos(
//                eyeSeeHwnd,
//                new WinDef.HWND(new Pointer(0)),
//                0,
//                -monitor.height,
//                1,
//                1,
//                0x0400
//        );

//        this.overlay.setSize(1, 1);
//        this.overlay.setLocation(0, -monitor.height);
    }

    /**
     * Stops an ongoing drawing task and starts a new one with the specified FPS limit.
     */
    public void restartDrawingTask(int fpsLimit) {
        if (redrawTask != null) {
            redrawTask.cancel(false);
        }

        long delay = 1_000_000_000L / fpsLimit;
        redrawTask = EyeSee.EXECUTOR.scheduleAtFixedRate(this::tick, delay, delay, TimeUnit.NANOSECONDS);
    }

    @Override
    public boolean isShowing() {
        return currentlyShowing;
    }

    private Rectangle getYoinkArea(WinDef.HWND hwnd) {
        Rectangle rectangle;
        if (hwnd == null) {
            rectangle = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        } else {
            WinDef.RECT rect = new WinDef.RECT();
            User32.INSTANCE.GetClientRect(hwnd, rect);
            rectangle = new Rectangle(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
        }
        // TODO: figure these out better
        int width = 60;
        int height = 580;
        return new Rectangle((int) rectangle.getCenterX() - width / 2, (int) rectangle.getCenterY() - height / 2, width, height);
    }

    @Override
    public void dispose() {
        overlay.dispose();
        super.dispose();
    }
}