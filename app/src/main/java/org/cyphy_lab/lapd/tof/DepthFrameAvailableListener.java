package org.cyphy_lab.lapd.tof;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import org.cyphy_lab.lapd.core.CVManager;
import org.cyphy_lab.lapd.core.DataBuffers;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;

import java.nio.ByteBuffer;

public class DepthFrameAvailableListener implements ImageReader.OnImageAvailableListener {

    private static final String TAG = DepthFrameAvailableListener.class.getSimpleName();


    private static int WIDTH = -1;
    private static int HEIGHT = -1;

    private final DepthFrameVisualizer depthFrameVisualizer;
    private final byte[] confidenceImage;
    private final int[] depthImageRaw;

    public static int getWIDTH() {
        return WIDTH;
    }

    public static int getHEIGHT() {
        return HEIGHT;
    }

    // Reusable bitmap for confidence value
    private final Bitmap depthOrConfidenceBitmap;

    /**
     * Listener for Android's ImageReader. Triggered when a new preview image frame is available.
     * Processes DEPTH16 frames from camera into a 1D short array that is sent to the
     * DepthFrameVisualizer class.
     *
     * @param depthFrameVisualizer Class to pass processed bytes to for visualization.
     */
    public DepthFrameAvailableListener(DepthFrameVisualizer depthFrameVisualizer, int width, int height) {
        this.depthFrameVisualizer = depthFrameVisualizer;
        WIDTH = width;
        HEIGHT = height;
        int size = WIDTH * HEIGHT;
        confidenceImage = new byte[size];
        depthImageRaw = new int[size];
        DataBuffers.initRawMaskBuffer(size);
        depthOrConfidenceBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_4444);
    }

    /**
     * Fired when a new preview frame is sent from the camera.
     * Processes the image if required, and sends processed bytes to depth frame visualizer
     * Also saves current image to file IF we are recording.
     *
     * @param reader ImageReader instance to read new frames.
     */
    @Override
    public void onImageAvailable(ImageReader reader) {
//        long currTime = System.currentTimeMillis();
//        float rate = (float) (1.0/((currTime - lastFired)/1000.0));
//        Log.i(TAG, "onImageAvailable: Fired rate " + rate);
//        lastFired = currTime;
        try {
            Image image = reader.acquireNextImage();
            if (image != null && image.getFormat() == ImageFormat.DEPTH16) {
                processAndPublishImageCV(image);
            }
            if (image != null) {
                image.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquireNextImage (probably) in onImageAvailable: ", e);
        }
    }


    /**
     * Receives a depth16 frame from camera, processes the frames through OpenCV to get a matrix m
     * Converts matrix to bitmap and sends it to the depth frame visualizer
     *
     * @param image
     */
    private void processAndPublishImageCV(Image image) {
        ByteBuffer buf = image.getPlanes()[0].getBuffer();
        // Publish raw depth16 image for network transmission later
        buf.get(DataBuffers.depth16FromToFSensorInternal);

        int index;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                index = y * WIDTH + x;
                byte normalizedConfidence = (byte) ((((DataBuffers.depth16FromToFSensorInternal[index * 2 + 1] >> 5 & 0x7) / (float) 7.0 * 255)));
                short depth = (short) ((DataBuffers.depth16FromToFSensorInternal[index * 2 + 1] & 0x1F) << 8 | (DataBuffers.depth16FromToFSensorInternal[index * 2] & 0xFF));
                confidenceImage[index] = normalizedConfidence;
                depthImageRaw[index] = depth;
            }
        }

        // Publish opencv processed image
        Mat m = CVManager.getInstance().ProcessConfidenceAndDepthFrame(confidenceImage, depthImageRaw, getHEIGHT());

        // We need the (raw confidence and depth image) + (unpublished final blobs)
        // Then, crop all the locations that are indicated by the final blobs

        // Synchronized update of the depth16 frame and the final blobs corresponding to that frame
        // VERY important that they are published together to the onDrawFrame thread.
        // onDrawFrame will lock on this synchronizer when it is copying the data
        DataBuffers.depthAndBlobDataSynchronizerLock.lock();
        try {
            System.arraycopy(DataBuffers.depth16FromToFSensorInternal, 0, DataBuffers.depth16FromToFSensorForRGB, 0, DataBuffers.depth16FromToFSensorInternal.length);
            CVManager.getInstance().published_final_blobs = CVManager.getInstance().unpublished_final_blobs;
            CVManager.getInstance().published_forfov_blobs = CVManager.getInstance().unpublished_forfov_blobs;
        } finally {
            // We will unlock regardless of exceptions
            DataBuffers.depthAndBlobDataSynchronizerLock.unlock();
        }

        Utils.matToBitmap(m, depthOrConfidenceBitmap);
        // Publish raw depth for now
        Mat depth1Channel = new MatOfInt(depthImageRaw);
        depth1Channel = depth1Channel.reshape(0, getHEIGHT());
        depthFrameVisualizer.onRawDataAvailable(depthOrConfidenceBitmap, depth1Channel);


        // Cleanup
        depthOrConfidenceBitmap.eraseColor(Color.TRANSPARENT);
        m.release();
        depth1Channel.release();
    }


}
