package org.cyphy_lab.lapd.arcore;

import org.cyphy_lab.lapd.config.PhoneLidarConfig;
import org.cyphy_lab.lapd.util.VectorUtils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


public class ObjectBbox {
    private static final String TAG = ObjectBbox.class.getSimpleName();

    public RelativeAnchor midpointAnchor;
    public RelativeAnchor topLeftAnchor;
    public RelativeAnchor bottomRightAnchor;

    /**
     * Mapping between [distance to object] ==> [the maximum observed connected component size].
     * Stored in reverse order (furthest to closest)
     */
    private final TreeMap<Float, Integer> tofDistanceToMaxCCSizeMap = new TreeMap<>(Collections.reverseOrder());

    private float last_distance_m = 0.0f;
    private float best_distance_m = 0.0f;
    private boolean isWithinIdealDistance = false;
    private boolean tooClose = false;

    // Graphing
    private final float DISTANCE_PER_TICK_M = 0.01f;
    private final int NUM_COLS = 1;
    private final int NUM_ROWS = (int) ((PhoneLidarConfig.DEPTH_FAR_ENOUGH_DISTANCE_M - PhoneLidarConfig.DEPTH_CLOSE_ENOUGH_DISTANCE_M) / DISTANCE_PER_TICK_M) + 1;
    private final Size matSize = new Size(NUM_COLS, NUM_ROWS);
    // R channel
    private final Mat overLimitMat = Mat.zeros(matSize, CvType.CV_8UC1);
    // G channel
    private final Mat underLimitMat = Mat.zeros(matSize, CvType.CV_8UC1);
    // B channel is 0
    private final Mat oneChannelZeros = Mat.zeros(matSize, CvType.CV_8UC1);
    // 0 scalar
    private static final Scalar zero = new Scalar(0);

    // State indicating if we are done scanning this bbox
    private boolean is2DScanComplete = false;

    // Is the object currently visible?
    private boolean visible = false;


    public ObjectBbox(RelativeAnchor midpointAnchor, RelativeAnchor topLeftAnchor, RelativeAnchor bottomRightAnchor) {
        this.midpointAnchor = midpointAnchor;
        this.topLeftAnchor = topLeftAnchor;
        this.bottomRightAnchor = bottomRightAnchor;
    }

    public void addTofDistanceCCObservation(float distance_m, int maxSizeCC) {
        // Likely an erroneous distance value, exit
        if (distance_m == 0.0f) return;

        // Round to 2 dp to have less bins
        distance_m = Math.round(distance_m * 100.0f) / 100.0f;
        last_distance_m = distance_m;

        tofDistanceToMaxCCSizeMap.putIfAbsent(distance_m, maxSizeCC);
    }

    /**
     * Go through the treemap in order of largest to smallest distance.
     * Once we see a maxCC value above our limit, step back some fixed value of metres and return that value.
     *
     * @return Naive best distance to scan the object from
     */
    public float computeNaiveBestDistance() {
        for (Map.Entry<Float, Integer> entry : tofDistanceToMaxCCSizeMap.entrySet()) {
            int maxCC = entry.getValue();
            if (maxCC > PhoneLidarConfig.DEPTH_MAX_CC_THRESHOLD) {
                float detected_distance = entry.getKey();
                float safe_distance = detected_distance + PhoneLidarConfig.DEPTH_DISTANCE_BACKOFF_M;
                // If we are TOO close to the object, this object just wasn't shiny enough. Recommend the min dist.
                return Math.max(safe_distance, PhoneLidarConfig.MINIMUM_ACCEPTABLE_DISTANCE_TO_OBJECT_M);
            }
        }
        // We never saw a point where the object was saturated. Return safe distance.
        return PhoneLidarConfig.MINIMUM_ACCEPTABLE_DISTANCE_TO_OBJECT_M;
    }

    /**
     * Indicates the computed ideal Z distance to this object
     *
     * @param distance_m
     */
    public void setBestDistance(float distance_m) {
        this.best_distance_m = distance_m;
    }

    /**
     * Return if we have enough data to decide the ideal distance to object
     *
     * @return
     */
    public boolean isDepthScanComplete() {
        if (tofDistanceToMaxCCSizeMap.size() == 0) return false;

        boolean closeEnough = tofDistanceToMaxCCSizeMap.lastKey() <= PhoneLidarConfig.DEPTH_CLOSE_ENOUGH_DISTANCE_M;
        boolean farEnough = tofDistanceToMaxCCSizeMap.firstKey() >= PhoneLidarConfig.DEPTH_FAR_ENOUGH_DISTANCE_M;
        boolean enoughPoints = tofDistanceToMaxCCSizeMap.size() > PhoneLidarConfig.DEPTH_ENOUGH_POINTS;
//        Log.w(TAG, String.format("Close Enough? %b (%f <= %f) \t Far Enough? %b (%f >= %f) \t Enough Points? %b (%d > %d)",
//                closeEnough, tofDistanceToMaxCCSizeMap.lastKey(), PhoneLidarConfig.DEPTH_CLOSE_ENOUGH_DISTANCE_M,
//                farEnough, tofDistanceToMaxCCSizeMap.firstKey(), PhoneLidarConfig.DEPTH_FAR_ENOUGH_DISTANCE_M,
//                enoughPoints, tofDistanceToMaxCCSizeMap.size(), PhoneLidarConfig.DEPTH_ENOUGH_POINTS));
        return closeEnough && farEnough && enoughPoints;
    }

    public String getObjectDistanceString() {
        StringBuilder s = new StringBuilder();
        s.append("\nToF Bbox Distance <=> max CC size mapping metrics:\n");
        for (Map.Entry<Float, Integer> entry : tofDistanceToMaxCCSizeMap.entrySet()) {
            s.append(Math.round(entry.getKey() * 10000.0) / 10000.0); // Distance
            s.append("\t==>\t");
            s.append(entry.getValue());
            s.append("\n");
        }
//        s.append("\nZ Bbox Distance <=> max CC size mapping metrics:\n");
//        for (Map.Entry<Float, Integer> entry : zDistanceToMaxCCSizeMap.entrySet()) {
//            s.append(Math.round(entry.getKey() * 10000.0) / 10000.0); // Distance
//            s.append("\t==>\t");
//            s.append(entry.getValue());
//            s.append("\n");
//        }
        return s.toString();
    }

    /**
     * Creates an RGB matrix representing PhoneLidarConfig.DEPTH_FAR_ENOUGH_DISTANCE_M --> PhoneLidarConfig.DEPTH_CLOSE_ENOUGH_DISTANCE_M
     * and all 1 cm steps along the way.
     * For each distance step:
     * Black ==> No data collected
     * Green ==> Data collected and maxCC is below limit (PhoneLidarConfig.DEPTH_MAX_CC_THRESHOLD)
     * Red ==> Data collected and maxCC is above limit (PhoneLidarConfig.DEPTH_MAX_CC_THRESHOLD)
     *
     * @param showAllDistancesAsGreen Don't show over-saturated areas as red, just show all as green.
     */
    public Mat distanceMapToMat(boolean showAllDistancesAsGreen) {
        overLimitMat.setTo(zero);
        underLimitMat.setTo(zero);

        // Temp array for values
        double[] val = new double[1];
        val[0] = 255.0;

        int idx = 0;
        // Set the right channel to red or green based on the maxCC at each distance
        for (float dist = PhoneLidarConfig.DEPTH_FAR_ENOUGH_DISTANCE_M;
             dist >= PhoneLidarConfig.DEPTH_CLOSE_ENOUGH_DISTANCE_M;
             dist -= DISTANCE_PER_TICK_M) {
            dist = Math.round(dist * 100.0f) / 100.0f;

            // Set current location to yellow
            if (dist == this.last_distance_m) {
                overLimitMat.put(idx, 0, val);
                underLimitMat.put(idx, 0, val);
                idx++;
                continue;
            }

            Integer maxCC = tofDistanceToMaxCCSizeMap.get(dist);
//            Log.v(TAG, String.format("Dist %f => MaxCC %d", dist, maxCC));
            if (maxCC != null) {
                if (showAllDistancesAsGreen) {
                    // Everything gets shown as green if we are in "user" UI mode
                    underLimitMat.put(idx, 0, val);
                } else if (maxCC > PhoneLidarConfig.DEPTH_MAX_CC_THRESHOLD) {
                    overLimitMat.put(idx, 0, val);
                } else {
                    underLimitMat.put(idx, 0, val);
                }
            }
            idx++;
        }

        // Put current location in yellow


        // put RGB channels together
        List<Mat> listMat = Arrays.asList(overLimitMat, underLimitMat, oneChannelZeros);
        Mat distanceBarMat = new Mat();
        Core.merge(listMat, distanceBarMat);
        Core.rotate(distanceBarMat, distanceBarMat, Core.ROTATE_90_COUNTERCLOCKWISE);
        return distanceBarMat;
    }

    public Mat getIdealDistanceMat(float currentDistance_m) {
        overLimitMat.setTo(zero);
        underLimitMat.setTo(zero);

        // Temp array for values
        double[] val = new double[1];
        val[0] = 255.0;


        int idx = 0;
        // Set the right channel to red or green based on the maxCC at each distance
        for (float dist = PhoneLidarConfig.DEPTH_FAR_ENOUGH_DISTANCE_M;
             dist >= PhoneLidarConfig.DEPTH_CLOSE_ENOUGH_DISTANCE_M;
             dist -= DISTANCE_PER_TICK_M) {
            dist = Math.round(dist * 100.0f) / 100.0f;
            currentDistance_m = Math.round(currentDistance_m * 100.0f) / 100.0f;

            if (dist == currentDistance_m) {
                // Set current location to yellow
                overLimitMat.put(idx, 0, val);
                underLimitMat.put(idx, 0, val);
            } else if (withinIdealDistance(dist)) {
                // Green if within ideal distance range
                underLimitMat.put(idx, 0, val);
            } else {
                // Red everywhere else
                overLimitMat.put(idx, 0, val);
            }
            idx++;
        }

        // put RGB channels together
        List<Mat> listMat = Arrays.asList(overLimitMat, underLimitMat, oneChannelZeros);
        Mat distanceBarMat = new Mat();
        Core.merge(listMat, distanceBarMat);
        Core.rotate(distanceBarMat, distanceBarMat, Core.ROTATE_90_COUNTERCLOCKWISE);
        return distanceBarMat;
    }

    public void updateWithinIdealDistance(float distance_m) {
        this.isWithinIdealDistance = withinIdealDistance(distance_m);
        this.tooClose = isTooClose(distance_m);
    }

    public boolean getIsWithinIdealDistance() {
        return this.isWithinIdealDistance;
    }

    public boolean getIsTooClose() {
        return this.tooClose;
    }

    public void set2DScanComplete() {
        this.is2DScanComplete = true;
    }

    public boolean get2DScanComplete() {
        return this.is2DScanComplete;
    }

    private boolean isTooClose(float distance_m) {
        return distance_m <= best_distance_m + PhoneLidarConfig.IDEAL_DISTANCE_MARGIN;
    }

    private boolean withinIdealDistance(float distance_m) {
        return distance_m >= best_distance_m - PhoneLidarConfig.IDEAL_DISTANCE_MARGIN &&
                isTooClose(distance_m);
    }

    public boolean withinValidRegionRelativePos(float[] position, float padding_m) {
        float tlx = this.topLeftAnchor.relativeAvgDrawPos[0];
        float tly = this.topLeftAnchor.relativeAvgDrawPos[1];
        float brx = this.bottomRightAnchor.relativeAvgDrawPos[0];
        float bry = this.bottomRightAnchor.relativeAvgDrawPos[1];
        float px = position[0];
        float py = position[1];

        float x1 = Math.min(tlx, brx);
        float y1 = Math.min(tly, bry);
        float x2 = Math.max(tlx, brx);
        float y2 = Math.max(tly, bry);

//        Log.d(TAG, String.format("OLD X1: %f, Y1: %f, X2: %f, Y2: %f, PX: %f, PY: %f", x1, y1, x2, y2, px, py));

        x1 -= padding_m / 2;
        x2 += padding_m / 2;
        y1 -= padding_m / 2;
        y2 += padding_m / 2;

//        Log.d(TAG, String.format("X1: %f, Y1: %f, X2: %f, Y2: %f, PX: %f, PY: %f", x1, y1, x2, y2, px, py));

        return VectorUtils.pointInside2D(x1, y1, x2, y2, px, py);
    }


    public void setNotVisible() {
        this.visible = false;
    }

    public void setAsVisible() {
        this.visible = true;
    }

    public boolean isVisible() {
        return this.visible;
    }
}
