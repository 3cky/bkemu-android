/*
 * Created: 16.02.2012
 *
 * Copyright (C) 2012 Victor Antonovich (v.antonovich@gmail.com)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package su.comp.bk.ui;

import su.comp.bk.R;
import su.comp.bk.arch.Computer;
import su.comp.bk.arch.io.VideoController;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Emulator screen view.
 */
public class BkEmuView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = BkEmuView.class.getName();

    // Rendering framerate, in frames per second
    private static final int RENDERING_FRAMERATE = 25;
    // Rendering period, in milliseconds.
    // RENDERING_FRAMERATE = 1000 / RENDERING_PERIOD
    private static final int RENDERING_PERIOD = (1000 / RENDERING_FRAMERATE);

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

    // FPS drawing enabled flag
    protected static boolean isFpsDrawingEnabled = true;
    // Low FPS value
    private final static int FPS_LOW_VALUE = 10;
    // Low FPS drawing color
    private final static int FPS_COLOR_LOW = Color.RED;
    // Normal FPS drawing color
    private final static int FPS_COLOR_NORMAL = Color.GREEN;

    // Computer screen aspect ratio
    private final static float COMPUTER_SCREEN_ASPECT_RATIO = (4f / 3f);

    private final FpsIndicatorUpdateRunnable fpsIndicatorUpdateRunnable =
    			new FpsIndicatorUpdateRunnable();
    protected TextView fpsIndicator;
    protected String fpsIndicatorString;

    // UI update handler
    private final Handler uiUpdateHandler;

    // UI surface render thread
    private BkEmuViewRenderingThread renderingThread;

    private GestureDetector gestureDetector;

    protected Matrix videoBufferBitmapTransformMatrix;

    protected Computer computer;

    /*
     * Gesture listener
     */
    class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // TODO Handle double tap
            return true;
        }
    }

	/*
	 * Surface view rendering thread
	 */
	class BkEmuViewRenderingThread extends Thread {
		private final SurfaceHolder surfaceHolder;
		private boolean isRunning = true;

		public BkEmuViewRenderingThread(SurfaceHolder holder) {
			this.surfaceHolder = holder;
		}

		public void stopRendering() {
			isRunning = false;
		}

		@Override
		public void run() {
	        long timeStamp;
	        long timeDelta;
	        Canvas canvas;
	        VideoController videoController = computer.getVideoController();
			while (isRunning) {
				timeStamp = System.currentTimeMillis();
				// Repaint surface
	            canvas = surfaceHolder.lockCanvas(null);
	            if (canvas != null) {
	                try {
	                    synchronized (surfaceHolder) {
	                        canvas.drawColor(Color.BLACK);
	                        if (computer != null) {
	                            canvas.drawBitmap(videoController.renderVideoBuffer(),
	                                    videoBufferBitmapTransformMatrix, null);
	                        }
	                    }
	                } finally {
	                    surfaceHolder.unlockCanvasAndPost(canvas);
	                }
	            }
	            long currentTime = System.currentTimeMillis();
	            updateFpsCounters(currentTime);
	            // Calculate time spent to canvas repaint
                timeDelta = currentTime - timeStamp;
                if (timeDelta < RENDERING_PERIOD) {
                    try {
                        Thread.sleep(RENDERING_PERIOD - timeDelta);
                    } catch (InterruptedException e) {
                    }
                } else {
                    Thread.yield();
                }
                // Calculate full elapsed time
                timeDelta = (int) (System.currentTimeMillis() - timeStamp);
			}
		}
	}

	/**
	 * FPS indicator update task (scheduled via UI update handler)
	 */
	class FpsIndicatorUpdateRunnable implements Runnable {
		@Override
		public void run() {
            // Set indicator color based on FPS value
			fpsIndicator.setTextColor((fpsValue > FPS_LOW_VALUE) ?
					FPS_COLOR_NORMAL : FPS_COLOR_LOW);
			// Set indicator FPS value text
			fpsIndicator.setText(String.format(fpsIndicatorString, fpsValue));
			// Set visibility flag
			fpsIndicator.setVisibility(isFpsDrawingEnabled ? VISIBLE : INVISIBLE);
		}
	}

	public BkEmuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = new GestureDetector(context, new GestureListener());
        this.uiUpdateHandler = new Handler();
        // Set surface events listener
        SurfaceHolder surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
	}

    public void setComputer(Computer computer) {
        this.computer = computer;
    }

    public void setFpsDrawingEnabled(boolean isEnabled) {
        isFpsDrawingEnabled = isEnabled;
    }

    protected void updateFpsCounters(long currentTime) {
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
                if (isFpsDrawingEnabled) {
                	// Update FPS indicator
                	uiUpdateHandler.post(fpsIndicatorUpdateRunnable);
                }
            }
        }
        // Store new timestamp value
        fpsCountersUpdateTimestamp = currentTime;
    }

    private void updateVideoBufferBitmapTransformMatrix(int viewWidth, int viewHeight) {
        Log.d(TAG, "update transform matrix, w:" + viewWidth + ", h:" + viewHeight);
        Bitmap videoBufferBitmap = computer.getVideoController().getVideoBuffer();
        int bitmapWidth = videoBufferBitmap.getWidth();
        int bitmapHeight = videoBufferBitmap.getHeight();
        float bitmapAspectRatio = (float) bitmapWidth / bitmapHeight;
        videoBufferBitmapTransformMatrix = new Matrix();
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
        videoBufferBitmapTransformMatrix.setScale(bitmapScaleX, bitmapScaleY);
        videoBufferBitmapTransformMatrix.postTranslate(bitmapTranslateX, bitmapTranslateY);
    }

	/* (non-Javadoc)
	 * @see android.view.SurfaceHolder.Callback#surfaceChanged(android.view.SurfaceHolder, int, int, int)
	 */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	    Log.d(TAG, "surface changed");
        // Update emulator screen bitmap scale matrix
        updateVideoBufferBitmapTransformMatrix(width, height);
	}

	/* (non-Javadoc)
	 * @see android.view.SurfaceHolder.Callback#surfaceCreated(android.view.SurfaceHolder)
	 */
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	    Log.d(TAG, "surface created");
	    // Update emulator screen bitmap scale matrix
	    updateVideoBufferBitmapTransformMatrix(getWidth(), getHeight());
        // Get FPS indicator resources
        this.fpsIndicatorString = getContext().getString(R.string.fps_string);
        this.fpsIndicator = (TextView) ((FrameLayout) getParent())
        		.findViewById(R.id.fps_indicator);
		this.renderingThread = new BkEmuViewRenderingThread(holder);
		this.renderingThread.start();
	}

	/* (non-Javadoc)
	 * @see android.view.SurfaceHolder.Callback#surfaceDestroyed(android.view.SurfaceHolder)
	 */
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surface destroyed");
		this.renderingThread.stopRendering();
		while (renderingThread.isAlive()) {
			try {
				this.renderingThread.join();
			} catch (InterruptedException e) {
			}
		}
        Log.d(TAG, "rendering stopped");
	}

    /* (non-Javadoc)
     * @see android.view.View#onTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

}
