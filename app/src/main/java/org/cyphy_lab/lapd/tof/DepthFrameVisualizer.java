package org.cyphy_lab.lapd.tof;

import android.graphics.Bitmap;

import org.opencv.core.Mat;

public interface DepthFrameVisualizer {
    void onRawDataAvailable(Bitmap bitmap, Mat depthOverlay);
}
