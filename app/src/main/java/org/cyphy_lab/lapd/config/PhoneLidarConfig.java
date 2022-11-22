package org.cyphy_lab.lapd.config;

import com.cyphy_lab.lapd.BuildConfig;

import java.util.HashMap;
import java.util.Map;

public class PhoneLidarConfig {
    //////////////////////////
    // SINGLE FRAME FILTERS //
    //////////////////////////

    // Maximum number of saturated pixels per camera blob
    // REASON: Empirical:, we don't get more than 3x3 sized blobs at our expected range
    public static int MAX_SIZE_CONNECTED_COMPONENT = 9;

    // Minimum ratio of area of blob to the area of its bounding box.
    // REASON: Calculating the mathematical ratio of perfect circle area to bbox area gives ~0.78.
    public static double MIN_COMPACTNESS = 0.75;

    // Maximum difference between bounding box width and height in pixels ("squareness" of box)
    // REASON: Empirical: this allows for a circular blob with a little slop due to low spatial resolution
    public static int MAX_BBOX_WIDTH_HEIGHT_DIFFERENCE_PX = 1;

    // Minimum and maximum allowable detection range
    // REASON: Empirical: sensor min and max range (20 - 100 cm) to see a camera (test rig), with some slop
    private static int KNOWN_RANGE_MIN_CM = 10;
    private static int KNOWN_RANGE_MAX_CM = 150;
    // Don't change the two below, calculated through setters
    public static int KNOWN_RANGE_MIN = KNOWN_RANGE_MIN_CM * 10;
    public static int KNOWN_RANGE_MAX = KNOWN_RANGE_MAX_CM * 10;


    ////////////////
    // ML FILTERS //
    ////////////////
    public static float DEPTH_NORM_MM = 10.0f;
    public static float MIN_CAMERA_PREDICTION_PROBABILITY = 0.5f;


    ///////////////////
    // DEPTH ROUTINE //
    ///////////////////
    // Anything more than 5 x 5 is not OK
    public static int DEPTH_MAX_CC_THRESHOLD = 25;
    public static float DEPTH_DISTANCE_BACKOFF_M = 0.05f;
    public static float MINIMUM_ACCEPTABLE_DISTANCE_TO_OBJECT_M = 0.6f;
    public static float DEPTH_CLOSE_ENOUGH_DISTANCE_M = 0.3f;
    public static float DEPTH_FAR_ENOUGH_DISTANCE_M = 0.8f;
    public static float IDEAL_DISTANCE_MARGIN = 0.05f;
    public static int DEPTH_ENOUGH_POINTS = (int) (DEPTH_FAR_ENOUGH_DISTANCE_M - DEPTH_CLOSE_ENOUGH_DISTANCE_M) * 80; // 80 => 8 points per 10 cm of distance


    /////////////////////////
    // 3D TEMPORAL FILTERS //
    /////////////////////////

    // Distance above which a 3D point is considered to be part of a new / separate anchor
    // REASON: Empirical: inaccuracies in 3D position determination won't affect us too much
    public static final double ANCHOR_STICKY_RADIUS = 0.06;

    // How many frames to wait before removing anchors that are too small
    // REASON: Gives users around 10 seconds to get some new information about cameras
    // DISABLED: The full camera scan should take care of it
    public static final int ANCHOR_HEATMAP_REMOVE_TOOSMALL_ANCHORS_FREQUENCY_FRAMES = 300;

    // Minimum threshold to consider an anchor as large enough to persist
    // REASON: Empirical.
    // Note: we get FOV_INFORMATION_GAINED_SCORE_INCREMENT per new valid pose.
    public static final float ANCHOR_MIN_SCORE = 5.0f;

    // Anchor score decay rate per frame
    // NOTE: We DO NOT use this now
    public static final float ANCHOR_SCORE_DECAY_RATE_PER_FRAME = 0.05f;

    // Anchor visualization: initial size, max size, and scaling rate parameters
    // REASON: Empirical: seems decent visually
    public static final float BASE_SPHERE_SCALE = 0.01f;
    public static final float MAX_SPHERE_SCALE = 0.015f;
    public static final int NUM_OCCURRENCES_TO_MAX_SIZE = 20;
    public static final float PER_OCCURRENCE_SCALE_FACTOR = (MAX_SPHERE_SCALE - BASE_SPHERE_SCALE) / (float) NUM_OCCURRENCES_TO_MAX_SIZE;

    // FOV filtering parameters
    // REASON: Mostly empirical, a real camera seems to get locked in a decent timeframe with these settings
    public static final float FOV_EPSILON_DISTANCE_FOR_NEW_POSE = 0.005f;
    // REASON: Calculated: 0.28 m something is the 70 cm - 10 degree FOV both sides calculated max dist
//    public static final float MAX_ALLOWED_DISTANCE_BEFORE_TOOLARGE_FOV = 0.35f;
    public static final float MAX_ALLOWED_DISTANCE_BEFORE_TOOLARGE_FOV = 0.35f;
    public static final float FOV_INFORMATION_GAINED_SCORE_INCREMENT = 5.0f;
    public static final float FOV_INFORMATION_THRESHOLD_SCORE = 30.0f;
    public static final int MAX_NUM_OUT_OF_FOV_STRIKES = 3;

    // Entropy grid system (Minimap)
    public static final int FRAMES_UNTIL_RESAMPLE = 5;
    //    public static final int MINIMAP_NUM_ROWS = 4;
//    public static final int MINIMAP_NUM_COLS = 8;

    // INCREASE THIS TO MAKE MINIMAP LESS ANNOYING. Comes at cost of less repeatability as users can move freely within box.
    public static final float MINIMAP_ROW_DIST_METERS = 0.025f;
    public static final float MINIMAP_COL_DIST_METERS = MINIMAP_ROW_DIST_METERS;


    // WARNING THERE IS A MAGIC NUMBER in entropyBitmapTransform that MUST BE CHANGED if ROWS/COLS is changed
    // Otherwise the grid will be OFF THE SCREEN SUBTLY and you WON'T COMPLETE THE GRID
    public static final int MINIMAP_NUM_ROWS = 8;
    public static final int MINIMAP_NUM_COLS = 16;
    public static final int MIN_UNIQUE_POSES_NEEDED = 1;

    // Conditions for putting down a trustworthy reference anchor
    // This is tuned for our test shelves with checkboard patterns (high number of valid pointcloud points)
    public static final int MIN_NUM_POINTCLOUD_POINTS = 140;
    public static final float MIN_POINTCLOUD_AVERAGE_CONFIDENCE = 0.4f;

    // Check assertions to avoid programmer error
    static {
        if (BuildConfig.DEBUG && !(FOV_INFORMATION_THRESHOLD_SCORE >= ANCHOR_MIN_SCORE)) {
            throw new AssertionError("Assertion failed: FOV_INFORMATION_MAX_SCORE >= ANCHOR_MIN_SCORE");
        }
    }

    // METRICS
    public static final boolean SHOULD_REPORT_METRICS = true;

    //////////////////////
    // UI ONLY SETTINGS //
    //////////////////////

    // Number of frames to skip before doing depth overlay again - saves CPU %
    public static final int DEPTH_FRAME_OVERLAY_SAMPLING_INTERVAL = 10;

    // UI warning distance for when the user is too close to the scene
    public static int WARN_TOOCLOSE_DISTANCE_MM = 500;

    // Delay before changing any filter values (to observe the shift)
    public static int SETTINGS_CHANGE_DELAY_MILLIS = 1500;

    // Default depth value if we don't get any useful info
    public static final double DEFAULT_DEPTH_VALUE_METRES = 0.7;


    // Moving speed warning
    public static final float MAX_MOVEMENT_SPEED_METERS_PER_SEC = 0.25f;
    public static final float MAX_MOVEMENT_SPEED_AVERAGING_TIME_SEC = 1.0f;

    // FPS warning
    public static final int MIN_TOF_FPS = 15;

    // Renderer settings
    public static final float DARKEN_BACKGROUND_RENDERER_MULTIPLER = 0.7f;
    public static final float[] DARKEN_BACKGROUND_RENDERER_MULTIPLER_ARRAY = new float[]{DARKEN_BACKGROUND_RENDERER_MULTIPLER,
            DARKEN_BACKGROUND_RENDERER_MULTIPLER, DARKEN_BACKGROUND_RENDERER_MULTIPLER, 1.0f};

    // Magic constants for transforms
    public static final float TAP_X_DIST_TO_METERS_RATIO = 0.0005f;

    ///////////////////////////////
    // PHONE IDENTIFIER SETTINGS //
    ///////////////////////////////

    // Important map of phone device IDs (also seems to be specific to phone + debugging computer combination)
    public static final Map<String, String> phoneID = new HashMap<String, String>() {{
        // Apply defaultManualTransform for an unknown phone
        put("default", "default");
        // S20+ - computer combinations used in testing
        put("92aab4a65ddb09d0", "S20_0");
        put("35afabc130188647", "S20_0");
        put("4c8d6b141edaf88e", "S20_0");
        put("2534cb0207b965e8", "S20_1");
        put("e83691ce231f1c8d", "S20_1");
        put("6ece2e77a3831bc6", "S20_1");

        // S20 Ultra 5G - computer combinations used in testing
        put("99f6bba13b8e8226", "S20ULTRA_0");
        put("b3213da83cd1b51c", "S20ULTRA_0");
        put("04e12d44986e038f", "S20ULTRA_0");
        put("3a1036b9000cd828", "S20ULTRA_0");
    }};


    public static final Map<String, float[]> manualTransformMap = new HashMap<String, float[]>() {{
        // Apply defaultManualTransform for an unknown phone
        put("default", defaultManualTransform);
        // S20+
        put("S20_0", manualTransformS20_0);
        put("S20_1", manualTransformS20_1);

        // S20 Ultra 5G
        put("S20ULTRA_0", manualTransformS20Ultra);
    }};


    // TODO: Change defaultManualTransform for your phone
    // TODO:
    // Manual transformation values of ToF coordinates to RGB coordinates.
    // Float array has the format:
    //      OFFSET_X, OFFSET_Y, SCALEFACTOR_X, SCALEFACTOR_Y
    //
    //      OFFSET values are simply added to the ToF coordinate to get the RGB coordinate
    //      SCALEFACTOR multiplies the ToF coordinate to get the RGB coordinate
    //      SCALEFACTOR is applied after the offset.
    //
    // See TransformationUtil.java -> blobLocationToImageNormalized
    // Default values are the same as S20 values just to have some sane defaults
    private static final float[] defaultManualTransform = new float[]{
            2.0f, 5.0f, 0.88f, 0.9f
    };
    private static final float[] manualTransformS20_0 = new float[]{
            2.0f, 5.0f, 0.88f, 0.9f
    };
    private static final float[] manualTransformS20_1 = new float[]{
            4.0f, 1.0f, 0.88f, 0.9f
    };
    private static final float[] manualTransformS20Ultra = new float[]{
            2.0f, 11.0f, 0.88f, 0.9f
    };


    /**
     * Map between phone model and the ToF camera dimensions
     * TODO: set this according to your phone model number
     * TODO: you may have to guess your ToF camera dimensions a few times till there is a consistent image
     */
    public static final Map<String, DepthCameraDimensions.CameraDimension> cameraDimensionMap = new HashMap<String, DepthCameraDimensions.CameraDimension>() {{
        // Note 10+
        put("samsung SM-N975F", new DepthCameraDimensions.CameraDimension(320, 240));
        // S20+
        put("samsung SM-G985F", new DepthCameraDimensions.CameraDimension(320, 240));
        // S20 Ultra 5G
        put("samsung SM-G988B", new DepthCameraDimensions.CameraDimension(320, 240));
        put("samsung SM-G988N", new DepthCameraDimensions.CameraDimension(320, 240));
    }};



    public static void setKnownRangeMinCm(int knownRangeMinCm) {
        KNOWN_RANGE_MIN_CM = knownRangeMinCm;
        KNOWN_RANGE_MIN = KNOWN_RANGE_MIN_CM * 10;
    }

    public static void setKnownRangeMaxCm(int knownRangeMaxCm) {
        KNOWN_RANGE_MAX_CM = knownRangeMaxCm;
        KNOWN_RANGE_MAX = KNOWN_RANGE_MAX_CM * 10;
    }
}
