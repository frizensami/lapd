package org.cyphy_lab.lapd.core;

import android.content.Context;
import android.util.Log;

import org.cyphy_lab.lapd.config.PhoneLidarConfig;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.abs;


public class CVManager {
    private static final String TAG = CVManager.class.getSimpleName();
    private static CVManager instance = null;

    private long lastFrameTime;
    private final BaseLoaderCallback mLoaderCallback;

    private final Mat Labels;
    private final Mat Stats;
    private final Mat Centroids;

    // For depth hints
    private Mat currentDepthMap;
    private final int[] currentDepthMapDepth = new int[1];

    // Allocate all arrays only once here (API is weird, needs "pointers" if-else in like this
    double[] centerCoord_x = new double[1];
    double[] centerCoord_y = new double[1];
    private final int[] topleftCoord_x = new int[1];
    private final int[] topleftCoord_y = new int[1];
    private final int[] rectWidth = new int[1];
    private final int[] rectHeight = new int[1];
    private final int[] rectArea = new int[1];

    // Semi-temporary final blob list - DepthFrameAvailableListener will update the published list together with the depth16 frame
    public List<Keypoint> unpublished_final_blobs = new ArrayList<>();
    public List<Keypoint> unpublished_forfov_blobs = new ArrayList<>();

    // This is what the RGB frame will read
    public List<Keypoint> published_final_blobs = new ArrayList<>();
    public List<Keypoint> published_forfov_blobs = new ArrayList<>();

    // This is the ROI that the RGB side is currently focusing on
    // We can choose to draw this on the CV frame or not
    private Rect rgbRoi = null;
    private final boolean showRgbRoi = true;

    // Filter pipeline on/off settings
    private boolean filterMaxSize = true;
    private boolean filterCompactness = false; // Ignore bbox area "fill" filtering
    private boolean filterBboxSquareness = true;
    private boolean filterInRange = true;
    private boolean filterML = true; // Using one of our main contributions of ML filtering


    private boolean filterSizeAtDepth = true; // NOT USED!

    // FPS settings to be accessed from main activity
    public double averageFps = 30.0;
    private int frameCounter = 0;
    private final int FPS_LOOKBACK_FRAMES = 30;
    private long firstFrameTimeMillis;
    public List<Keypoint> all_detected_blobs;

    // Color constants
    private final Scalar COLOR_GREEN = new Scalar(0, 255, 0);
    private final Scalar COLOR_RED = new Scalar(255, 0, 0);
    private final Scalar COLOR_BLUE = new Scalar(0, 0, 255);


    public static CVManager getInstance() { //  dirty singleton
        return instance;
    }

    public CVManager(Context context) {
        CVManager.instance = this; //  dirty singleton

        checkOpenCV();
        Labels = new Mat();
        Stats = new Mat();
        Centroids = new Mat();

        lastFrameTime = System.currentTimeMillis();

        mLoaderCallback = new BaseLoaderCallback(context) {
            @Override
            public void onManagerConnected(int status) {
                if (status == LoaderCallbackInterface.SUCCESS) {
                    Log.i(TAG, "OpenCV loaded successfully");
                    Log.i(TAG, "Camera Settings Set");
                } else {
                    super.onManagerConnected(status);
                }
            }
        };
    }


    public void onResume(Context context) {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, context, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void setFilterSettingsFromSettingsArray(boolean[] settingsArray) {
        this.filterMaxSize = settingsArray[0];
        this.filterCompactness = settingsArray[1];
        this.filterBboxSquareness = settingsArray[2];
        this.filterSizeAtDepth = settingsArray[3];
        this.filterInRange = settingsArray[4];
        this.filterML = settingsArray[5];
    }

    public void setSettingsArrayFromFilterSettings(boolean[] settingsArray) {
        settingsArray[0] = this.filterMaxSize;
        settingsArray[1] = this.filterCompactness;
        settingsArray[2] = this.filterBboxSquareness;
        settingsArray[3] = this.filterSizeAtDepth;
        settingsArray[4] = this.filterInRange;
        settingsArray[5] = this.filterML;
    }


    private void checkOpenCV() {
        if (!OpenCVLoader.initDebug())
            Log.e(TAG, "Unable to load OpenCV");
        else
            Log.i(TAG, "OpenCV loaded");
    }


    /**
     * Given a user-drawn ROI on the screen, returns the average depth of the area in the box, excluding 0-values
     */
    public float getDepthAtXYInMetresWithSmartROI(float X1, float Y1, float X2, float Y2) {
        // Find the min x and y because how we draw the ROI box affects whether X1 > X2 or Y1 > Y2, for e.g.
        // If we don't do this the roi size could be negative
        int start_x = (int) Math.min(X1, X2);
        int start_y = (int) Math.min(Y1, Y2);

        int y_roi = (int) Math.abs(Y1 - Y2);
        int x_roi = (int) Math.abs(X1 - X2);

        Rect roi = new Rect(start_x, start_y, x_roi, y_roi);

        try {
            Mat depthRoi = currentDepthMap.submat(roi);
            Mat flattenedRoi = depthRoi.clone().reshape(1, 1); // 1 channel, 1 row, all in columns
            Core.sort(flattenedRoi, flattenedRoi, Core.SORT_EVERY_ROW | Core.SORT_ASCENDING);

            // Get the middle element of the sorted array except any zeroes
            int roiSize = depthRoi.rows() * depthRoi.cols();
            int numNonzero = Core.countNonZero(flattenedRoi);
            if (numNonzero == 0) return 0; // Nothing to do here, no nonzero elements
            int indexOfFirstNonzero = roiSize - numNonzero;
            // First index + (distance between first and last possible index)/2
            // Here we get the 5th percentile index
            int medianIdx = (int) (indexOfFirstNonzero + ((roiSize - 1 - indexOfFirstNonzero) * 0.05f));
            int[] dist = new int[1];
            flattenedRoi.get(0, medianIdx, dist);
            return (float) dist[0] / 1000.0f;
        } catch (Exception ex) {
            Log.e(TAG, "Exception during depth ROI boxing", ex);
            return 0;
        }
    }


    /**
     * Given a user-drawn ROI on the screen, returns the maximum connected component size in the ROI
     * Note: We don't check for any of the taps being outside of the ROI
     */
    public int getMaxCCAtXYWithROI(float X1, float Y1, float X2, float Y2) {
        // Find the min x and y because how we draw the ROI box affects whether X1 > X2 or Y1 > Y2, for e.g.
        // If we don't do this the roi size could be negative
        int start_x = (int) Math.min(X1, X2);
        int start_y = (int) Math.min(Y1, Y2);

        int y_roi = (int) Math.abs(Y1 - Y2);
        int x_roi = (int) Math.abs(X1 - X2);

//        Log.w(TAG, String.format("\n \nTopleft (x, y): (%d, %d)\nBottomright (x, y): (%d, %d)\nX Roi: %d, Y ROI: %d\n", topleft_x, topleft_y, bottomright_x, bottomright_y, x_roi, y_roi));
//        Log.w(TAG, String.format("\n \nTap1 (x, y): (%f, %f)\nTap 2 (x, y): (%f, %f)\nMax X: %d, Max Y: %d\n", tapX1, tapY1, tapX2, tapY2, imageMaxX, imageMaxY));

        // Compute the require ROI
        Rect roi = new Rect(start_x, start_y, x_roi, y_roi);
//        // Save it so that the main cv thread can display it
        this.rgbRoi = roi;

        // DON'T run connected components again, expensive. Just check the latest CC list.
        // For each keypoint, detect if keypoint.location is within the roi
        // If it is, see if we need to update the current running total of connected component sizes
        int maxCCSize = 0;
        for (Keypoint k : all_detected_blobs) {
            if (k.location.inside(roi)) {
                maxCCSize = Math.max(maxCCSize, (int) k.size);
            }
        }
        return maxCCSize;
    }


    /**
     * Swap the blob location x and y to get the depth X Y. This doesn't transform any coordinates.
     *
     * @param depthX
     * @param depthY
     * @return
     */
    private double getBlobDepthFromCurrentDepthMap(int depthX, int depthY) {
        currentDepthMap.get(depthY, depthX, currentDepthMapDepth);
        return (float) (currentDepthMapDepth[0] / 1000.0);
    }

    /**
     * Uses an ROI to get the average depth around the point.
     *
     * @param depthX
     * @param depthY
     * @param roiSize
     * @return
     */
    private double getBlobDepthFromCurrentDepthMapWithROI(int depthX, int depthY, int roiSize) {
        int topleft_x = Math.max(depthY - (roiSize / 2), 0);
        int topleft_y = Math.max(depthX - (roiSize / 2), 0);

        int roiSizeWidth = roiSize;
        int roiSizeHeight = roiSize;

        // ROI can be outside the range: just reduce ROI size if it's outside the bounds
        if (topleft_x + roiSize >= currentDepthMap.cols()) {
            roiSizeWidth = currentDepthMap.cols() - topleft_x - 1;
        }
        if (topleft_y + roiSize >= currentDepthMap.rows()) {
            roiSizeHeight = currentDepthMap.rows() - topleft_y - 1;
        }

        Rect roi = new Rect(topleft_x, topleft_y, roiSizeWidth, roiSizeHeight);
        try {
            Mat depthRoi = currentDepthMap.submat(roi);
            int depthAvg = (int) (Core.sumElems(depthRoi).val[0] / (float) Core.countNonZero(depthRoi));
            return depthAvg / 1000.0;
        } catch (Exception ex) {
            Log.e(TAG, "Exception during depth ROI boxing", ex);
            return 0;
        }
    }

    /**
     * @param confidenceImage Confidence values normalized between 0 and 255 from an original range of 0 - 7
     * @param depthImageRaw
     * @param height
     * @return
     */
    public Mat ProcessConfidenceAndDepthFrame(byte[] confidenceImage, int[] depthImageRaw, int height) {
        // FPS
        double fps = updateFps();

        // Empty list of blobs we will be filtering
        List<Keypoint> blobs;
        List<Keypoint> ml_filtered_blobs;

        // Convert raw image into CV2 representation (height = number of rows)
        Mat confidence1Channel = new MatOfByte(confidenceImage); // For some reason it ONLY works if MatofByte is used, normal Mat(... Bytebuffer) breaks
        confidence1Channel = confidence1Channel.reshape(0, height); // Reshaping to image shape because MatofByte constructor is 1D. cn is channel
        Mat confidence1ChannelRaw = confidence1Channel.clone();
        Mat depth1Channel = new MatOfInt(depthImageRaw);
        depth1Channel = depth1Channel.reshape(0, height);
        currentDepthMap = depth1Channel;

        // Copy existing 1 channel
        Mat confidence3Channel = new Mat(); //the actual three channel image that is sent to bitmapConverter
        Imgproc.cvtColor(confidence1Channel, confidence3Channel, Imgproc.COLOR_GRAY2RGB);

        ////////////////////////////
        // ACTUAL FILTERING BELOW //
        ////////////////////////////

        // Mask the confidence channel to get only 0s - MODIFIES confidence1Channel
        PreprocessFrame(confidence1Channel);

        // Blob detector (find_blobs equivalent from Python side)
        blobs = FindBlobs(confidence1Channel);

        // Save the raw blob output for later use (network transmission)
        this.all_detected_blobs = blobs;

        // Single-frame filtering to remove blobs
        blobs = ApplySingleFrameBlobFilters(blobs);


        if (filterML) {
            // Have to clear this now since we don't clear this anywhere else
            unpublished_forfov_blobs.clear();
            // Filter blobs using pre-trained model
            ml_filtered_blobs = ApplyMachineLearningBlobFilters(confidence1ChannelRaw, depth1Channel, blobs);
            // Store the final blobs for external access
            unpublished_final_blobs = ml_filtered_blobs;
//          unpublished_forfov_blobs  --> updated inside the ML filter for sake of speed
        } else {
            unpublished_final_blobs = blobs;
            unpublished_forfov_blobs.clear();
        }

        // Get the depth hint for the final blobs
        getDepthForFinalBlobs(unpublished_final_blobs);

        // Draw bounding boxes on image, maybe draw FPS
        drawOnImage(confidence3Channel, blobs, unpublished_final_blobs, false, fps);

        return confidence3Channel;
    }


    private void PreprocessFrame(Mat confidenceImage) {
        // Mask of only the high reflectivity areas
        Core.inRange(confidenceImage, new Scalar(0), new Scalar(0), confidenceImage); //only taking values 0 - 10. Refer to python pipeline, but its for the connectedComponents().
    }


    private List<Keypoint> FindBlobs(Mat confidenceImage) {
        // Mask of only the high reflectivity areas
        List<Keypoint> blobs = new ArrayList<>();
        Imgproc.connectedComponentsWithStats(confidenceImage, Labels, Stats, Centroids, 4);

        // Drawing the bounding boxes
        for (int i = 1; i < Centroids.rows(); i++) {
            Centroids.get(i, 0, centerCoord_x);
            Centroids.get(i, 1, centerCoord_y);
            Stats.get(i, 0, topleftCoord_x);
            Stats.get(i, 1, topleftCoord_y);
            Stats.get(i, 2, rectWidth);
            Stats.get(i, 3, rectHeight);
            Stats.get(i, 4, rectArea);

            Keypoint keypoint = new Keypoint();
            keypoint.location = new Point(centerCoord_x[0], centerCoord_y[0]);
            keypoint.size = rectArea[0];
            keypoint.width = rectWidth[0];
            keypoint.height = rectHeight[0];
            keypoint.topLeft = new Point(topleftCoord_x[0], topleftCoord_y[0]);
            keypoint.blob_number = i;

            blobs.add(keypoint);
        }

        return blobs;
    }


    private List<Keypoint> ApplySingleFrameBlobFilters(List<Keypoint> blobs) {
        List<Keypoint> filteredBlobs = new ArrayList<>();
        for (Keypoint blob : blobs) {
            // Filter out by keypoint max size
            if (filterMaxSize && !isBlobSmallEnough(blob)) continue;
            // Filter out by keypoint max circularity
            if (filterCompactness && !isBlobCompact(blob)) continue;
            // Filter by the bbox squares (n pixel height / width difference)
            if (filterBboxSquareness && !isBlobBboxSquare(blob)) continue;
            // Filter out keypoints based on depth-information (bounding boxes should be not too large)
            // if (filterSizeAtDepth !isBlobSmallEnoughAtThisDepth(blob, depthImageRaw)) continue;
            // Filter out points based on whether they are too close/far to be detected usually
            if (filterInRange && !isBlobInsideKnownDetectionRange(blob)) continue;
            filteredBlobs.add(blob);
        }
        return filteredBlobs;
    }


    /**
     * @param c1Channel     Confidence values represented between 0 and 255 (mapping from 0 - 7)
     * @param depth1Channel Depth values in mm, same dimension as confidence channel
     * @param blobs         List of Keypoints to filter
     * @return Filtered list of keypoints based on machine learning model
     */
    private List<Keypoint> ApplyMachineLearningBlobFilters(Mat c1Channel, Mat depth1Channel, List<Keypoint> blobs) {

        c1Channel.convertTo(c1Channel, CvType.CV_32FC1);
        Mat d1Channel = new Mat(c1Channel.rows(), c1Channel.cols(), CvType.CV_32FC1);
        depth1Channel.convertTo(d1Channel, CvType.CV_32FC1);

        float roi_size = 5;
        float[] imageBuf = new float[5 * 5 * 2];

        List<Keypoint> mlFilteredBlobs = new ArrayList<>();
        for (Keypoint blob : blobs) {
            Point localTopLeft = blob.topLeft.clone();

            // Same algorithm as roi.py's extract_roi
            localTopLeft.x = (int) (localTopLeft.x + (blob.width / 2.0) - (roi_size / 2.0));
            localTopLeft.y = (int) (localTopLeft.y + (blob.height / 2.0) - (roi_size / 2.0));

            double bottomRightX = localTopLeft.x + roi_size; // check against width
            double bottomRightY = localTopLeft.y + roi_size; // check against height
            // Outside the bounds
            if (localTopLeft.x < 0 || localTopLeft.y < 0 || bottomRightX >= c1Channel.width() || bottomRightY >= c1Channel.height()) {
                continue;
            }
            Point localBottomRight = new Point(bottomRightX, bottomRightY);

            // Note: https://stackoverflow.com/questions/22710264/convert-two-points-to-a-rectangle-cvrect
            // Says that the bottom right point is EXCLUSIVE...
            Rect roi = new Rect(localTopLeft, localBottomRight);
            Mat depthChannel = d1Channel.submat(roi).clone();
            Mat confChannel = c1Channel.submat(roi);
            Mat depthChannelToSort;
            Mat confDepthRoi = new Mat(c1Channel.rows(), c1Channel.cols(), CvType.CV_32FC2);

            Core.divide(confChannel, new Scalar(255.0), confChannel);
            // Left-rotate remap conf values
            remapConfValues(confChannel);

            // Flattening
            depthChannelToSort = depthChannel.reshape(1, 1);
            // Sort to get median in center
            Core.sort(depthChannelToSort, depthChannelToSort, Core.SORT_ASCENDING | Core.SORT_EVERY_ROW);
            // Get middle pixel value (median)
            int medianIdx = (confChannel.rows() * confChannel.cols()) / 2;
            float[] medianDepth = new float[1];
            depthChannelToSort.get(0, medianIdx, medianDepth);
//            Log.d(TAG, "Median depth at idx: " + medianIdx + " is " + medianDepth[0]);
            // Subtract the min/max value from the depthh
            Core.subtract(depthChannel, new Scalar(medianDepth[0]), depthChannel);
            clipAndNormalizeDepth(depthChannel);

            List<Mat> confDepthList = Arrays.asList(depthChannel, confChannel);
            Core.merge(confDepthList, confDepthRoi);
//            printRoi(confDepthRoi);

//            Log.d(TAG, String.format("conf depth h, w, c: (%d, %d, %d)", confDepthRoi.height(), confDepthRoi.width(), confDepthRoi.channels()));
            confDepthRoi.get(0, 0, imageBuf);
            float probability = FilterModel.runInferenceImage(imageBuf);
            if (probability > PhoneLidarConfig.MIN_CAMERA_PREDICTION_PROBABILITY) {
                Log.d(TAG, "Blob with probability " + probability + ", adding to blob list");
                mlFilteredBlobs.add(blob);
            } else {
                // These blobs don't make the cut but are potentially reflective surfaces that should be FOV-filtered out
                unpublished_forfov_blobs.add(blob);
            }
        }

        return mlFilteredBlobs;
    }

    static final float remapVal = (1.0f / 255.0f);
    float[] val = new float[1];

    private void remapConfValues(Mat c1Channel) {
        for (int r = 0; r < c1Channel.rows(); r++) {
            for (int c = 0; c < c1Channel.cols(); c++) {
                c1Channel.get(r, c, val);
                val[0] = val[0] - remapVal;
                if (val[0] < 0.0) {
                    val[0] = 1.0f;
                }
                c1Channel.put(r, c, val);
            }
        }
    }

    private void clipAndNormalizeDepth(Mat depthChannel) {
        for (int r = 0; r < depthChannel.rows(); r++) {
            for (int c = 0; c < depthChannel.cols(); c++) {
                depthChannel.get(r, c, val);
                if (val[0] > PhoneLidarConfig.DEPTH_NORM_MM) {
                    val[0] = PhoneLidarConfig.DEPTH_NORM_MM;
                } else if (val[0] < -PhoneLidarConfig.DEPTH_NORM_MM) {
                    val[0] = -PhoneLidarConfig.DEPTH_NORM_MM;
                }
                val[0] += PhoneLidarConfig.DEPTH_NORM_MM;
                val[0] /= PhoneLidarConfig.DEPTH_NORM_MM * 2;

                depthChannel.put(r, c, val);

            }
        }
    }

    private void printRoi(Mat confDepthRoi) {
        float[] val = new float[2];
        StringBuilder builder = new StringBuilder();
        builder.append("Printing channel 0 and channel 1\n");
        for (int h = 0; h < confDepthRoi.channels(); h++) {
            for (int r = 0; r < confDepthRoi.rows(); r++) {
                for (int c = 0; c < confDepthRoi.cols(); c++) {
                    confDepthRoi.get(r, c, val);
                    builder.append(Math.round(val[h] * 100.0) / 100.0);
                    builder.append(" \t");
                }
                builder.append('\n');
            }
            builder.append('\n');
            builder.append('\n');
        }
        Log.d(TAG, builder.toString());
    }

    // Python: filter_by_keypoint_size
    private boolean isBlobSmallEnough(Keypoint blob) {
        return blob.size < PhoneLidarConfig.MAX_SIZE_CONNECTED_COMPONENT;
    }


    // New: keypoint circularity but not by pixels
    private boolean isBlobCompact(Keypoint blob) {
        // Compactness == Area of blob / (bbox width * bbox height)
        // For an ideal circle, compactness = pi / 4
        // For 1 pixel, compactness is = 1
        // Threshold should be something like >= 0.75?
        double compactness = ((double) blob.size) / ((double) blob.width * (double) blob.height);
        return compactness >= PhoneLidarConfig.MIN_COMPACTNESS;
    }

    // Python: filter_by_keypoint_circularity
    private boolean isBlobBboxSquare(Keypoint blob) {
        return abs(blob.width - blob.height) <= PhoneLidarConfig.MAX_BBOX_WIDTH_HEIGHT_DIFFERENCE_PX;
    }

    // Python: filter_by_known_depth_range_refactor
    private boolean isBlobInsideKnownDetectionRange(Keypoint blob) {
        // We already calculate the depth with ROIs so just use that instead of single depth value
        return blob.depthMetres == 0 || (blob.depthMetres >= PhoneLidarConfig.KNOWN_RANGE_MIN && blob.depthMetres <= PhoneLidarConfig.KNOWN_RANGE_MAX);
    }

    /**
     * Find the depth value for all final filtered blobs.
     * Try to get it at the exact blob center. Otherwise, get from the average of the ROI around the blob
     *
     * @param final_blobs
     */
    private void getDepthForFinalBlobs(List<Keypoint> final_blobs) {
        // Check depth XY position for each blob
        for (Keypoint blob : final_blobs) {
            // Get depth at exact XY point
            double depthAtPoint = getBlobDepthFromCurrentDepthMap((int) blob.location.x, (int) blob.location.y);
            if (depthAtPoint != 0) {
                // Found depth at the exact point
                blob.depthMetres = depthAtPoint;
//                Log.e("BLOB DEPTH POINT", "Blob depth at x: " + blob.location.x + " y: " + blob.location.y + " is " + blob.depthMetres);
                continue;
            }
            // Get depth in 10 or 20 pixel ROI around point
            double depthAt10PixelROI = getBlobDepthFromCurrentDepthMapWithROI((int) blob.location.x, (int) blob.location.y, 10);
            if (depthAt10PixelROI != 0) {
                // Found depth at the exact point
                blob.depthMetres = depthAt10PixelROI;
//                Log.e("BLOB DEPTH 10 ROI", "Blob depth at x: " + blob.location.x + " y: " + blob.location.y + " is " + blob.depthMetres);
                continue;
            }
            double depthAt20PixelROI = getBlobDepthFromCurrentDepthMapWithROI((int) blob.location.x, (int) blob.location.y, 20);
            if (depthAt20PixelROI != 0) {
                // Found depth at the exact point
                blob.depthMetres = depthAt20PixelROI;
//                Log.e("BLOB DEPTH 20 ROI", "Blob depth at x: " + blob.location.x + " y: " + blob.location.y + " is " + blob.depthMetres);
                continue;
            }
            // If all else fails, set depth to default value
            blob.depthMetres = PhoneLidarConfig.DEFAULT_DEPTH_VALUE_METRES;

        }
    }


    // Display confidence map and bounding boxes of blobs
    private void drawOnImage(Mat confidence3Channel, List<Keypoint> blobs, List<Keypoint> filtered_blobs, boolean drawFPS, double fps) {
        // Some points and scalars needed to draw things on the image
        Point ImageCorner = new Point(0, confidence3Channel.height());


        // Drawing Fps Counter
        if (drawFPS) {
            Imgproc.putText(
                    confidence3Channel,
                    String.format("Fps: %2.0f  ", fps),
                    ImageCorner,
                    Imgproc.FONT_HERSHEY_PLAIN,
                    0.5,
                    COLOR_GREEN,
                    1
            );
        }

        int margin = 1;
        // Drawing the bounding boxes
        for (int i = 0; i < blobs.size(); i++) {
            Keypoint blob = blobs.get(i);
            Point localTopLeft = blob.topLeft.clone();
            // Add margin
            localTopLeft.x = Math.max(localTopLeft.x - margin, 0);
            localTopLeft.y = Math.max(localTopLeft.y - margin, 0);
            double bottomRightX = Math.min(localTopLeft.x + blob.width + margin, confidence3Channel.width());
            double bottomRightY = Math.min(localTopLeft.y + blob.height + margin, confidence3Channel.height());
            Point localBottomRight = new Point(bottomRightX, bottomRightY);

            Imgproc.rectangle(
                    confidence3Channel,
                    localTopLeft,
                    localBottomRight,
                    COLOR_RED,
                    1
            );
        }

        // Drawing the bounding boxes
        for (int i = 0; i < filtered_blobs.size(); i++) {
            Keypoint blob = filtered_blobs.get(i);
            Point localTopLeft = blob.topLeft.clone();
            // Add margin
            localTopLeft.x = Math.max(localTopLeft.x - margin, 0);
            localTopLeft.y = Math.max(localTopLeft.y - margin, 0);
            double bottomRightX = Math.min(localTopLeft.x + blob.width + margin, confidence3Channel.width());
            double bottomRightY = Math.min(localTopLeft.y + blob.height + margin, confidence3Channel.height());
            Point localBottomRight = new Point(bottomRightX, bottomRightY);

            Imgproc.rectangle(
                    confidence3Channel,
                    localTopLeft,
                    localBottomRight,
                    COLOR_GREEN,
                    1
            );
        }

        // Draw the rgb bounding ROI
        if (showRgbRoi && rgbRoi != null) {
            Imgproc.rectangle(
                    confidence3Channel,
                    rgbRoi.tl(),
                    rgbRoi.br(),
                    COLOR_BLUE,
                    1
            );
        }
        // Core.rotate(image,image,Core.ROTATE_90_CLOCKWISE);
    }

    public void resetRgbRoi() {
        rgbRoi = null;
    }

    /**
     * Updates current and averaged FPS. Should be called for every new frame.
     *
     * @return FPS from this frame and last frame timing (current FPS).
     */
    private double updateFps() {
        this.frameCounter++;
        long currentTimeMillis = System.currentTimeMillis();

        // Average FPS calculations
        if (this.frameCounter == 1) {
            // First frame in the set, set up last frame time
            this.firstFrameTimeMillis = currentTimeMillis;
        } else if (this.frameCounter >= this.FPS_LOOKBACK_FRAMES) {
            // Done with seeing all frames, get average FPS
            double secondsElapsed = (currentTimeMillis - this.firstFrameTimeMillis) / 1000.0f;
            double averageFps = this.frameCounter / secondsElapsed;
            this.averageFps = averageFps;
            this.frameCounter = 0;
        }

        // Instant FPS calculation
        double instantFps = 1000.0 / (currentTimeMillis - lastFrameTime);
        lastFrameTime = currentTimeMillis;

        return instantFps;
    }


}
