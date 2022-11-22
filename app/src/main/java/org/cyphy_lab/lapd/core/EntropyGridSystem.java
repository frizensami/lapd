package org.cyphy_lab.lapd.core;

import android.util.Log;

import com.google.ar.core.Pose;

import org.cyphy_lab.lapd.config.PhoneLidarConfig;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EntropyGridSystem {
    private static final String TAG = EntropyGridSystem.class.getSimpleName();

    // dynamically determined by the valid region size
    private final float ENTROPY_GRID_HALF_WIDTH;
    private final float ENTROPY_GRID_HALF_HEIGHT;
    public int NUM_ROWS;
    public int NUM_COLS;

    // class members
    private final ArrayList<Pose>[][] entropyGrid;

    private static Pose initialPose;
    private static Pose currentPose;
    private static int currentColNum;
    private static int currentRowNum;

    // flag of the system should start
    private static boolean isStarted = false;

    // Cached value, updated after each pose update
    private boolean minimapComplete = false;
    private boolean minimapNewlyComplete = false;

    // constructor (do nothing)
    public EntropyGridSystem(float half_width_m, float half_height_m, float col_size_m, float row_size_m) {
        ENTROPY_GRID_HALF_WIDTH = half_width_m;
        ENTROPY_GRID_HALF_HEIGHT = half_height_m;
        NUM_COLS = (int) Math.ceil(half_width_m / col_size_m);
        NUM_ROWS = (int) Math.ceil(half_height_m / row_size_m);

        // Bitmap transform doesn't seem to work if we don't have an even number of rows and cols
        if (NUM_COLS % 2 != 0) NUM_COLS++;
        if (NUM_ROWS % 2 != 0) NUM_ROWS++;

        entropyGrid = new ArrayList[NUM_ROWS][NUM_COLS];

        // Initialize all entropy pose grid arraylists
        for (int i = 0; i < NUM_ROWS; i++) {
            for (int j = 0; j < NUM_COLS; j++) {
                entropyGrid[i][j] = new ArrayList<>();
            }
        }

        Log.w(TAG, String.format("Entropy grid created:\nHalf width: %f\nHalf height %f\nNum Cols: %d\nNum Rows: %d\n", half_width_m, half_height_m, NUM_COLS, NUM_ROWS));

    }

    public boolean getIsStarted() {
        return isStarted;
    }

    public void setIsStarted(boolean start) {
        isStarted = start;
    }

    /**
     * Completion percentage for the whole grid
     *
     * @return
     */
    public int getCompletionPercentage() {
        int totalGridBoxes = NUM_COLS * NUM_ROWS;
        int completedBoxes = 0;
        for (int i = 0; i < NUM_ROWS; i++) {
            for (int j = 0; j < NUM_COLS; j++) {
                if (entropyGrid[i][j].size() >= PhoneLidarConfig.MIN_UNIQUE_POSES_NEEDED) {
                    completedBoxes++;
                }
            }
        }
        int completionPercentage = (int) Math.ceil((float) completedBoxes / (float) totalGridBoxes * 100.0f);
//        Log.d(TAG, "Minimap cols: " + MINIMAP_NUM_COLS + " rows: " + MINIMAP_NUM_ROWS + "completed boxes: " + completedBoxes + " %: " + completionPercentage);
        return completionPercentage;
    }

    /**
     * Completion percentage for the currently selected square of the entropy grid
     *
     * @return
     */
    public int getCurrentPositionCompletionPercentage() {
        if (currentRowNum >= NUM_ROWS || currentRowNum < 0 || currentColNum >= NUM_COLS || currentColNum < 0)
            return 0; // Out of bounds check

        int maxProgress = PhoneLidarConfig.MIN_UNIQUE_POSES_NEEDED;
        int currentProgress = entropyGrid[currentRowNum][currentColNum].size();
        return (int) Math.ceil((float) currentProgress / (float) maxProgress * 100.0f);
    }

    // set starting pose
    public void setInitialPose(Pose startingPose) {
        initialPose = startingPose;
    }

    // set current pose
    public void setCurrentPose(Pose pose) {
        currentPose = pose;
    }

    public void clearEntropyGrid() {
        for (ArrayList<Pose>[] row : entropyGrid) {
            for (ArrayList<Pose> val : row) {
                val.clear();
            }
        }
    }

    public List<Integer> getDirectionArrow() {
        List<Integer> resultDirections = new ArrayList<>();
        if (initialPose == null || currentPose == null) {
            resultDirections.add(0);
            return resultDirections;
        }

        float[] currentPos = currentPose.getTranslation();
        float[] initialPos = initialPose.getTranslation();
        float horizontalDisplacement = currentPos[0] - initialPos[0];
        float verticalDisplacement = currentPos[1] - initialPos[1];

        // 0 -> no arrow is needed, because the position is already shown in the mini-map
        // 1 -> arrow to the right when position exceeds the left boundary
        // 2 -> arrow to the left when position exceeds the right boundary
        // 3 -> arrow to the top when position exceeds the bottom boundary
        // 4 -> arrow to the bottom when position exceeds the top boundary

        // check within boundary
        boolean withInBoundary = Math.abs(horizontalDisplacement) < ENTROPY_GRID_HALF_WIDTH
                && Math.abs(verticalDisplacement) < ENTROPY_GRID_HALF_HEIGHT;
        if (withInBoundary) {
            resultDirections.add(0);
            return resultDirections;
        }

        // check left and right boundaries
        if (horizontalDisplacement <= -ENTROPY_GRID_HALF_WIDTH) {
            // exceed left, should point to right
            resultDirections.add(1);
        }
        if (horizontalDisplacement >= ENTROPY_GRID_HALF_WIDTH) {
            // exceed right, should point to left
            resultDirections.add(2);
        }

        // check top and bottom boundaries
        if (verticalDisplacement <= -ENTROPY_GRID_HALF_HEIGHT) {
            // exceed bottom, should point to top
            resultDirections.add(3);
        }

        if (verticalDisplacement >= ENTROPY_GRID_HALF_HEIGHT) {
            // exceed top, should point to bottom
            resultDirections.add(4);
        }

        return resultDirections;
    }

    // return grid column index based on horizontal displacement
    private int findGridColNum(float horizontalDisplacement) {
        // check within valid range
        if (Math.abs(horizontalDisplacement) >= ENTROPY_GRID_HALF_WIDTH) {
            return -1; // for invalid displacement
        }

        // 0 - numCols / 2 - 1 --> negative direction
        // numCols / 2 - numCols - 1 --> positive direction
        int leftStart = 0;
        int rightStart = NUM_COLS / 2;
        int length = NUM_COLS / 2;
        int colNum;
        // check direction of displacement
        if (horizontalDisplacement >= 0) {
            // positive direction, higher displacement -> higher col number
            colNum = rightStart + (int) Math.floor(horizontalDisplacement / ENTROPY_GRID_HALF_WIDTH * length);
        } else {
            // negative direction, higher abs displacement -> lower col number
            horizontalDisplacement = Math.abs(horizontalDisplacement);
            colNum = leftStart + (int) Math.floor((1 - horizontalDisplacement / ENTROPY_GRID_HALF_WIDTH) * length);
        }
        return colNum;
    }

    // return grid row index based on vertical displacement
    private int findGridRowNum(float verticalDisplacement) {
        // check within valid range
        if (Math.abs(verticalDisplacement) >= ENTROPY_GRID_HALF_HEIGHT) {
            return -1; // for invalid displacement
        }

        // swap direction
        verticalDisplacement = -verticalDisplacement;

        // 0 - numRows / 2 - 1 --> negative direction
        // numRows / 2 - numRows - 1 --> positive direction
        int leftStart = 0;
        int rightStart = NUM_ROWS / 2;
        int length = NUM_ROWS / 2;
        int rowNum;
        // check direction of displacement
        if (verticalDisplacement >= 0) {
            // positive direction, higher displacement -> higher col number
            rowNum = rightStart + (int) Math.floor(verticalDisplacement / ENTROPY_GRID_HALF_HEIGHT * length);
        } else {
            // negative direction, higher abs displacement -> lower col number
            verticalDisplacement = Math.abs(verticalDisplacement);
            rowNum = leftStart + (int) Math.floor((1 - verticalDisplacement / ENTROPY_GRID_HALF_HEIGHT) * length);
        }
        return rowNum;
    }

    public void updateGridByPose() {
        updateGridByPoseWithoutAddingPose();

        float[] currentPos = currentPose.getTranslation();
        // invalid if any -1 index
        if (currentColNum == -1 || currentRowNum == -1) {
            return; // won't add score
        }

        // Check if the pose is unique
        boolean informationGained = true;
        ArrayList<Pose> currentGridSquarePoses = entropyGrid[currentRowNum][currentColNum];
        for (Pose oldPose : currentGridSquarePoses) {
            float[] oldPosePosition = oldPose.getTranslation();
            double horizontalDistance = Math.sqrt(
                    Math.pow(oldPosePosition[0] - currentPos[0], 2)
                            + Math.pow(oldPosePosition[1] - currentPos[1], 2)
            );
            // check with epsilon distance
            if (horizontalDistance < PhoneLidarConfig.FOV_EPSILON_DISTANCE_FOR_NEW_POSE) {
                informationGained = false;
                break;
            }
        }

        // If unique enough, add to the current grid pose list
        if (informationGained) currentGridSquarePoses.add(currentPose);

        updateIsMinimapComplete();
    }

    public void updateGridByPoseWithoutAddingPose() {
        // check valid Pose
        if (initialPose == null || currentPose == null) return;

        // check isStarted
        if (!isStarted) return;

        // find the horizontal and vertical displacement from the initial pose
        // using the x and y component of translation is pretty decent
        // the dead reckoning error should be tolerable when the session is small
        float[] currentPos = currentPose.getTranslation();
        float[] initialPos = initialPose.getTranslation();
        float horizontalDisplacement = currentPos[0] - initialPos[0];
        float verticalDisplacement = currentPos[1] - initialPos[1];

        // get grid location
        currentColNum = findGridColNum(horizontalDisplacement);
        currentRowNum = findGridRowNum(verticalDisplacement);

        // invalid if any -1 index
        if (currentColNum == -1 || currentRowNum == -1) {
            return; // won't add score
        }
    }


    /**
     * Checks to see if all grid values are above the minimum score.
     */
    private void updateIsMinimapComplete() {
        for (int i = 0; i < NUM_ROWS; i++) {
            for (int j = 0; j < NUM_COLS; j++) {
                if (entropyGrid[i][j].size() < PhoneLidarConfig.MIN_UNIQUE_POSES_NEEDED) {
                    this.minimapComplete = false;
                    return;
                }
            }
        }

        // If the minimap wasn't complete RIGHT before this update, we are "newly complete"
        if (!this.minimapComplete) {
            this.minimapNewlyComplete = true;
        }
        this.minimapComplete = true;
    }


    /**
     * Are all grid values are above the minimum score?
     * Updated once per pose update.
     */
    public boolean isMinimapComplete() {
        return this.minimapComplete;
    }

    /**
     * Return true ONCE if the minimap just completed
     *
     * @return
     */
    public boolean isMinimapNewlyCompleteCallOnce() {
        if (this.minimapNewlyComplete) {
            this.minimapNewlyComplete = false;
            return true;
        } else {
            return false;
        }

    }

    /**
     * Create a matrix to represent the current state of the entropy grid, which can be converted to a bitmap later.
     * A red or yellow square indicates the current location, it's yellow if the position is completed.
     * Squares are green if we are not on them right now and we have completed them.
     * Otherwise, they are black.
     */
    public Mat getEntropyGridMat() {
        // "grid" indicates the positions where the square was completed
        double[][] grid = new double[NUM_ROWS][NUM_COLS];
        for (int i = 0; i < NUM_ROWS; i++) {
            for (int j = 0; j < NUM_COLS; j++) {
                if (entropyGrid[i][j].size() >= PhoneLidarConfig.MIN_UNIQUE_POSES_NEEDED) {
                    // only flip the grid ele to 1 when above some threshold
                    grid[i][j] = 255.0;
                }
            }
        }

        // G channel indicating the completed places
        Mat oneChannelEntropyGridMat = new Mat(new Size(NUM_COLS, NUM_ROWS), CvType.CV_8UC1);
        double[] gridFlatten = Arrays.stream(grid).flatMapToDouble(Arrays::stream).toArray();
        oneChannelEntropyGridMat.put(0, 0, gridFlatten);


        // Get the current location (for the R channel)
        double[][] currentLocationIndicator = new double[NUM_ROWS][NUM_COLS];
        if (currentColNum != -1 && currentRowNum != -1) {
            currentLocationIndicator[currentRowNum][currentColNum] = 255.0;
        }

        // R channel indicating the current location
        Mat oneChannelLocationMat = new Mat(new Size(NUM_COLS, NUM_ROWS), CvType.CV_8UC1);
        double[] locationFlatten = Arrays.stream(currentLocationIndicator).flatMapToDouble(Arrays::stream).toArray();
        oneChannelLocationMat.put(0, 0, locationFlatten);

        // B channel is 0
        Mat oneChannelZeros = Mat.zeros(oneChannelEntropyGridMat.size(), CvType.CV_8UC1);

        // put RGB channels together
        List<Mat> listMat = Arrays.asList(oneChannelLocationMat, oneChannelEntropyGridMat, oneChannelZeros);
        Mat entropyGridMat = new Mat();
        Core.merge(listMat, entropyGridMat);
        Core.rotate(entropyGridMat, entropyGridMat, Core.ROTATE_90_CLOCKWISE);
        return entropyGridMat;
    }


    public Pose getInitialPose() {
        return initialPose;
    }


}
