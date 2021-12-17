/*
 * Created: 16.02.2012
 *
 * Copyright (C) 2012 Victor Antonovich (v.antonovich@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package su.comp.bk.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.TextureView;

import androidx.core.content.ContextCompat;

import su.comp.bk.R;
import su.comp.bk.arch.Computer;
import su.comp.bk.arch.io.disk.FloppyController;
import su.comp.bk.arch.io.VideoController;
import su.comp.bk.arch.io.VideoController.DisplayMode;
import su.comp.bk.ui.BkEmuActivity.GestureListener;
import timber.log.Timber;

/**
 * Emulator screen view.
 */
public class BkEmuView extends TextureView implements TextureView.SurfaceTextureListener,
        VideoController.FrameSyncListener {
    // FPS value averaging time, in milliseconds
    private static final int FPS_AVERAGING_TIME = 1000;
    // FPS counters last update timestamp
    protected long fpsCountersUpdateTimestamp;
    // FPS frame counter
    protected int fpsFrameCounter;
    // FPS accumulated time counter
    protected int fpsAccumulatedTime;
    // FPS current value
    protected int fpsValue;

    // FPS indicator text size
    private static final float FPS_INDICATOR_SIZE = 30;
    // FPS indicator paint for drawing on the canvas
    private Paint fpsIndicatorPaint;
    private Rect fpsIndicatorBounds;
    // FPS indicator pattern string
    protected String fpsIndicatorString;
    // FPS indicator drawing enabled flag
    protected static boolean isFpsDrawingEnabled = false;
    // FPS indicator threshold for low FPS value
    private final static int FPS_LOW_VALUE = 10;
    // FPS indicator drawing color (low FPS values)
    private final static int FPS_INDICATOR_COLOR_LOW = Color.RED;
    // FPS indicator drawing color (normal FPS values)
    private final static int FPS_INDICATOR_COLOR_NORMAL = Color.GREEN;
    // FPS indicator alpha
    private static final int FPS_INDICATOR_ALPHA = 255;
    // FPS indicator background color
    private static final int FPS_INDICATOR_COLOR_BACK = Color.BLACK;
    // FPS indicator background alpha
    private static final int FPS_INDICATOR_ALPHA_BACK = 180;

    // Computer screen aspect ratio
    private final static float COMPUTER_SCREEN_ASPECT_RATIO = (4f / 3f);

    // Display mode indicator steady time (in milliseconds)
    private static final int DISPLAY_MODE_INDICATOR_STEADY_TIME = 350;
    // Display mode indicator timeout (in milliseconds)
    private static final int DISPLAY_MODE_INDICATOR_TIMEOUT = 650;
    // Display mode indicator alpha at the start (0 - transparent, 255 - opaque)
    private static final int DISPLAY_MODE_INDICATOR_ALPHA_START = 255;
    // Display mode indicator alpha at the end (0 - transparent, 255 - opaque)
    private static final int DISPLAY_MODE_INDICATOR_ALPHA_END = 0;
    // Display mode indicator bitmap: black and white
    private Bitmap displayModeBwIndicatorBitmap;
    // Display mode indicator bitmap: grayscale
    private Bitmap displayModeGrayscaleIndicatorBitmap;
    // Display mode indicator bitmap: color
    private Bitmap displayModeColorIndicatorBitmap;
    // Display mode indicator paint
    private Paint displayModeIndicatorPaint;
    // Display mode: last seen state
    private DisplayMode lastDisplayMode = null;
    // Display mode: last change timestamp
    private long lastDisplayModeChangeTimestamp;

    // Floppy controller activity indicator alpha (0 - transparent, 255 - opaque)
    private static final int FLOPPY_ACTIVITY_INDICATOR_ALPHA = 127;
    // Floppy controller activity indicator bitmap
    private Bitmap floppyActivityIndicatorBitmap;
    // Floppy controller activity indicator paint
    private Paint floppyActivityIndicatorPaint;
    // Floppy controller activity indicator timeout (in milliseconds)
    private static final int FLOPPY_ACTIVITY_INDICATOR_TIMEOUT = 250;
    // Floppy controller activity indicator timeout (in CPU ticks)
    protected long floppyActivityIndicatorTimeoutCpuTicks;
    // Floppy controller activity indicator last update time (in milliseconds)
    protected long lastFloppyActivityIndicatorUpdateTime;
    private boolean isFloppyActivityIndicatorVisible;

    // UI update thread
    private BkEmuViewUpdateThread uiUpdateThread;

    private GestureDetector gestureDetector;

    protected volatile Matrix videoBufferBitmapTransformMatrix;

    protected Computer computer;

    private int lastViewHeight;
    private int lastViewWidth;

    // Video controller frames per UI update
    private static final int FRAMES_PER_UPDATE = 3;
    private long lastUpdateFrameNumber;

    /*
     * Emulator view update thread
     */
    class BkEmuViewUpdateThread extends Thread {
        private final BkEmuView bkEmuView;
        private boolean isRunning = true;
        private boolean isUpdateScheduled = false;

        BkEmuViewUpdateThread(BkEmuView bkEmuView) {
            this.bkEmuView = bkEmuView;
        }

        void scheduleStop() {
            Timber.d("schedule update thread stop");
            isRunning = false;
            scheduleUpdate();
        }

        public synchronized void scheduleUpdate() {
            isUpdateScheduled = true;
            this.notifyAll();
        }

        @Override
        public void run() {
            Timber.d("update thread started");
            boolean isWaitingForUpdate;
            Canvas canvas;
            VideoController videoController = computer.getVideoController();
            int bgColor = ContextCompat.getColor(getContext(), R.color.theme_window_background);
            while (isRunning) {
                Computer comp = computer;
                if (comp != null && !comp.isPaused()) {
                    // Repaint canvas
                    canvas = bkEmuView.lockCanvas(null);
                    if (canvas != null) {
                        try {
                            synchronized (bkEmuView) {
                                canvas.drawColor(bgColor);
                                canvas.drawBitmap(videoController.renderVideoBuffer(),
                                        videoBufferBitmapTransformMatrix, null);
                                drawIndicators(canvas);
                            }
                        } finally {
                            bkEmuView.unlockCanvasAndPost(canvas);
                        }
                    }
                }

                synchronized (this) {
                    isWaitingForUpdate = !isUpdateScheduled;
                    if (isWaitingForUpdate) {
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
                            // Do nothing
                        }
                    }
                    isUpdateScheduled = false;
                }
                if (!isWaitingForUpdate) {
                    Thread.yield();
                }
            }
            Timber.d("update thread stopped");
        }
    }

    public BkEmuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Enable focus grabbing by view
        this.setFocusable(true);
        this.setFocusableInTouchMode(true);
        // Initialize emulator UI elements
        initFpsIndicator();
        initFloppyActivityIndicator();
        initDisplayModeIndicator();
        // Set surface events listener
        this.setSurfaceTextureListener(this);
    }

    public void setGestureListener(GestureListener listener) {
        gestureDetector = new GestureDetector(getContext(), listener);
        gestureDetector.setIsLongpressEnabled(true);
    }

    public void setComputer(Computer computer) {
        this.computer = computer;
        this.floppyActivityIndicatorTimeoutCpuTicks = computer.nanosToCpuTime(
                FLOPPY_ACTIVITY_INDICATOR_TIMEOUT * Computer.NANOSECS_IN_MSEC);
        computer.getVideoController().addFrameSyncListener(this);
    }

    public synchronized void setFpsDrawingEnabled(boolean isEnabled) {
        isFpsDrawingEnabled = isEnabled;
    }

    public synchronized boolean isFpsDrawingEnabled() {
        return isFpsDrawingEnabled;
    }

    private void initFpsIndicator() {
        fpsIndicatorString = getContext().getString(R.string.fps_string);
        fpsIndicatorBounds = new Rect();
        fpsIndicatorPaint = new Paint();
        fpsIndicatorPaint.setStyle(Paint.Style.FILL);
        fpsIndicatorPaint.setTextAlign(Paint.Align.LEFT);
        fpsIndicatorPaint.setTextSize(FPS_INDICATOR_SIZE);
    }

    protected void drawFpsIndicator(Canvas canvas, long currentTime) {
        // Update FPS counters
        if (fpsCountersUpdateTimestamp > 0) {
            fpsFrameCounter++;
            // Calculate time elapsed from last FPS counters update
            int timeDelta = (int) (currentTime - fpsCountersUpdateTimestamp);
            // Calculate FPS value
            fpsAccumulatedTime += timeDelta;
            if (fpsAccumulatedTime >= FPS_AVERAGING_TIME) {
                // Update FPS value
                fpsValue = 1000 * fpsFrameCounter / fpsAccumulatedTime;
                // Clear accumulated FPS frame and time
                fpsFrameCounter = 0;
                fpsAccumulatedTime = 0;
            }
        }
        // Draw FPS indicator, if enabled
        if (isFpsDrawingEnabled()) {
            String fpsText = String.format(fpsIndicatorString, fpsValue);
            fpsIndicatorPaint.getTextBounds(fpsText, 0, fpsText.length(), fpsIndicatorBounds);
            fpsIndicatorPaint.setColor(FPS_INDICATOR_COLOR_BACK);
            fpsIndicatorPaint.setAlpha(FPS_INDICATOR_ALPHA_BACK);
            fpsIndicatorBounds.offset(0, fpsIndicatorBounds.height());
            canvas.drawRect(fpsIndicatorBounds, fpsIndicatorPaint);
            int fpsColor = (fpsValue > FPS_LOW_VALUE) ? FPS_INDICATOR_COLOR_NORMAL
                    : FPS_INDICATOR_COLOR_LOW;
            fpsIndicatorPaint.setColor(fpsColor);
            fpsIndicatorPaint.setAlpha(FPS_INDICATOR_ALPHA);
            canvas.drawText(fpsText, 0, fpsIndicatorBounds.height(), fpsIndicatorPaint);
        }
        // Store new timestamp value
        fpsCountersUpdateTimestamp = currentTime;
    }

    private void initFloppyActivityIndicator() {
        floppyActivityIndicatorBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.floppy_disk);
        floppyActivityIndicatorPaint = new Paint();
        floppyActivityIndicatorPaint.setAlpha(FLOPPY_ACTIVITY_INDICATOR_ALPHA);
    }

    protected void drawFloppyActivityIndicator(Canvas canvas, long currentTime) {
        if (currentTime - lastFloppyActivityIndicatorUpdateTime >
                FLOPPY_ACTIVITY_INDICATOR_TIMEOUT) {
            // Check floppy drive activity indicator visibility
            FloppyController floppyController = computer.getFloppyController();
            if (floppyController != null && floppyController.isMotorStarted()) {
                long lastFloppyAccessCpuTimeElapsed = computer.getUptimeTicks() -
                        floppyController.getLastAccessCpuTime();
                isFloppyActivityIndicatorVisible = (lastFloppyAccessCpuTimeElapsed <
                        floppyActivityIndicatorTimeoutCpuTicks);
            } else {
                isFloppyActivityIndicatorVisible = false;

            }
            lastFloppyActivityIndicatorUpdateTime = currentTime;
        }
        if (isFloppyActivityIndicatorVisible) {
            int x = canvas.getWidth() - floppyActivityIndicatorBitmap.getWidth();
            int y = canvas.getHeight() - floppyActivityIndicatorBitmap.getHeight();
            canvas.drawBitmap(floppyActivityIndicatorBitmap, x, y,
                    floppyActivityIndicatorPaint);
        }
    }

    private void initDisplayModeIndicator() {
        Resources r = getResources();
        displayModeBwIndicatorBitmap = BitmapFactory.decodeResource(r, R.drawable.display_bw);
        displayModeGrayscaleIndicatorBitmap = BitmapFactory.decodeResource(r, R.drawable.display_gray);
        displayModeColorIndicatorBitmap = BitmapFactory.decodeResource(r, R.drawable.display_color);
        displayModeIndicatorPaint = new Paint();
    }

    private void drawDisplayModeIndicator(Canvas canvas, long currentTime) {
        DisplayMode currentDisplayMode = computer.getVideoController().getDisplayMode();
        if (lastDisplayMode != currentDisplayMode) {
            if (lastDisplayMode != null) {
                lastDisplayModeChangeTimestamp = currentTime;
            }
            lastDisplayMode = currentDisplayMode;
        }
        long elapsedTime = currentTime - lastDisplayModeChangeTimestamp;
        if (elapsedTime < DISPLAY_MODE_INDICATOR_TIMEOUT) {
            Bitmap displayModeBitmap;
            switch (lastDisplayMode) {
                case BW:
                    displayModeBitmap = displayModeBwIndicatorBitmap;
                    break;
                case GRAYSCALE:
                    displayModeBitmap = displayModeGrayscaleIndicatorBitmap;
                    break;
                default:
                    displayModeBitmap = displayModeColorIndicatorBitmap;
            }
            float drawX = (canvas.getWidth() - displayModeBitmap.getWidth()) / 2f;
            float drawY = (canvas.getHeight() - displayModeBitmap.getHeight()) / 2f;
            int alpha = DISPLAY_MODE_INDICATOR_ALPHA_START;
            if (elapsedTime > DISPLAY_MODE_INDICATOR_STEADY_TIME) {
                alpha = (int)(DISPLAY_MODE_INDICATOR_ALPHA_START -
                        (DISPLAY_MODE_INDICATOR_ALPHA_START - DISPLAY_MODE_INDICATOR_ALPHA_END)
                                * (elapsedTime - DISPLAY_MODE_INDICATOR_STEADY_TIME)
                                / (DISPLAY_MODE_INDICATOR_TIMEOUT - DISPLAY_MODE_INDICATOR_STEADY_TIME));
            }
            displayModeIndicatorPaint.setAlpha(alpha);
            canvas.drawBitmap(displayModeBitmap, drawX, drawY, displayModeIndicatorPaint);
        }
    }

    protected void drawIndicators(Canvas canvas) {
        long currentTime = System.currentTimeMillis();
        drawFloppyActivityIndicator(canvas, currentTime);
        drawFpsIndicator(canvas, currentTime);
        drawDisplayModeIndicator(canvas, currentTime);
    }

    public void updateVideoBufferBitmapTransformMatrix(int viewWidth, int viewHeight) {
        lastViewWidth = viewWidth;
        lastViewHeight = viewHeight;
        int bitmapWidth = VideoController.VIDEO_BUFFER_WIDTH;
        int bitmapHeight = VideoController.VIDEO_BUFFER_HEIGHT;
        float bitmapAspectRatio = (float) bitmapWidth / bitmapHeight;
        float bitmapTranslateX;
        float bitmapTranslateY;
        float bitmapScaleX;
        float bitmapScaleY;
        if (viewWidth > COMPUTER_SCREEN_ASPECT_RATIO * viewHeight) {
            bitmapScaleY = (float) viewHeight / bitmapHeight;
            bitmapScaleX = bitmapScaleY * COMPUTER_SCREEN_ASPECT_RATIO / bitmapAspectRatio;
            bitmapTranslateX = (viewWidth - bitmapWidth * bitmapScaleX) / 2f;
            bitmapTranslateY = 0f;
        } else {
            bitmapScaleX = (float) viewWidth / bitmapWidth;
            bitmapScaleY = bitmapAspectRatio * bitmapScaleX / COMPUTER_SCREEN_ASPECT_RATIO;
            bitmapTranslateX = 0f;
            bitmapTranslateY = 0f;
        }
        Matrix m = new Matrix();
        m.setScale(bitmapScaleX, bitmapScaleY);
        m.postTranslate(bitmapTranslateX, bitmapTranslateY);
        videoBufferBitmapTransformMatrix = m;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int originalWidth = MeasureSpec.getSize(widthMeasureSpec);
        int originalHeight = MeasureSpec.getSize(heightMeasureSpec);
        int calculatedHeight = (int) (originalWidth / COMPUTER_SCREEN_ASPECT_RATIO);
        int finalWidth = originalWidth;
        int finalHeight = calculatedHeight;
        if (calculatedHeight > originalHeight) {
            finalWidth = (int) (originalHeight * COMPUTER_SCREEN_ASPECT_RATIO);
            finalHeight = originalHeight;
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY));
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Timber.d("onSurfaceTextureAvailable");
        // Update emulator screen bitmap scale matrix
        updateVideoBufferBitmapTransformMatrix(getWidth(), getHeight());
        // Start emulator screen update thread
        this.uiUpdateThread = new BkEmuViewUpdateThread(this);
        this.uiUpdateThread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Timber.d("onSurfaceTextureSizeChanged");
        if (width != lastViewWidth || height != lastViewHeight) {
            // Update emulator screen bitmap scale matrix
            updateVideoBufferBitmapTransformMatrix(width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Timber.d("onSurfaceTextureDestroyed");
        uiUpdateThread.scheduleStop();
        while (uiUpdateThread.isAlive()) {
            try {
                uiUpdateThread.join();
            } catch (InterruptedException e) {
            }
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    /* (non-Javadoc)
     * @see android.view.View#onTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return (gestureDetector != null) ? gestureDetector.onTouchEvent(event)
                    : super.onTouchEvent(event);
    }

    @Override
    public void verticalSync(long frameNumber) {
        if (frameNumber - lastUpdateFrameNumber >= FRAMES_PER_UPDATE && uiUpdateThread != null) {
            lastUpdateFrameNumber = frameNumber;
            uiUpdateThread.scheduleUpdate();
        }
    }
}
