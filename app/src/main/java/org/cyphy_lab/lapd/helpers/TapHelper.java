/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cyphy_lab.lapd.helpers;

import android.content.Context;
import android.graphics.Point;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Helper to detect taps using Android GestureDetector, and pass the taps between UI thread and
 * render thread.
 */
public final class TapHelper implements OnTouchListener {
    private static final String TAG = TapHelper.class.getSimpleName();
    private final GestureDetector gestureDetector;
    private final BlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);

    public Point scrollStart = null;
    public Point scrollEnd = null;
    public long lastMotionEventFinishTime = 0;
    public boolean isInMotion = false;
    public boolean newScrollEventReady = false;

    /**
     * Creates the tap helper.
     *
     * @param context the application's context.
     */
    public TapHelper(Context context) {
        gestureDetector =
                new GestureDetector(
                        context,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                // Queue tap if there is space. Tap is lost if queue is full.
                                queuedSingleTaps.offer(e);
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }

                            @Override
                            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                                Log.d(TAG, "e1: " + e1.toString() + " e2: " + e2.toString() + " dX: " + distanceX + " dY: " + distanceY);
                                isInMotion = true;
                                scrollStart = new Point((int) e1.getX(), (int) e1.getY());
                                scrollEnd = new Point((int) e2.getX(), (int) e2.getY());
                                return true;
                            }
                        });
    }

    /**
     * Polls for a tap.
     *
     * @return if a tap was queued, a MotionEvent for the tap. Otherwise null if no taps are queued.
     */
    public MotionEvent poll() {
        return queuedSingleTaps.poll();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
            if (isInMotion) {
                isInMotion = false;
                newScrollEventReady = true;
                Log.d(TAG, "e1: Scroll motion event completed!");
            }
        }
        return gestureDetector.onTouchEvent(motionEvent);
    }

    public void resetScrollData() {
        scrollStart = null;
        scrollEnd = null;
        newScrollEventReady = false;
        isInMotion = false;
    }
}
