package org.cyphy_lab.lapd.arcore;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.Matrix;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

import org.cyphy_lab.lapd.config.PhoneLidarConfig;
import org.cyphy_lab.lapd.core.Keypoint;

import java.util.HashMap;
import java.util.Map;

public class TransformationUtil {

    // Final manual transform used
    private final float[] manualTransform;

    /**
     * Set transform parameters based on what phone we are using
     */
    @SuppressLint("HardwareIds")
    public TransformationUtil(Context activity) {
        String deviceString = Settings.Secure.getString(activity.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        Log.i(TAG, "phoneID: " + deviceString);
        deviceString = PhoneLidarConfig.phoneID.get(deviceString);
        if (deviceString == null) {
            deviceString = PhoneLidarConfig.phoneID.get("default");
        }
        Log.i(TAG, "phoneID: " + deviceString);
        this.manualTransform = PhoneLidarConfig.manualTransformMap.get(deviceString);
        assert (this.manualTransform != null);
    }

    private static final String TAG = TransformationUtil.class.getSimpleName();


    private final float[] adjCoords = new float[2];

    public float[] blobLocationToImageNormalized(Keypoint keypoint, float image_width, float image_height) {
        float CenterX = (float) keypoint.location.x;
        float CenterY = (float) keypoint.location.y;
        float OFFSET_X = manualTransform[0];
        float OFFSET_Y = manualTransform[1];
        float SCALEFACTOR_X = manualTransform[2];
        float SCALEFACTOR_Y = manualTransform[3];

        adjCoords[0] = ((Math.min(CenterX + OFFSET_X, image_width) / image_width) - 0.5f) * SCALEFACTOR_X + 0.5f;
        adjCoords[1] = ((Math.min(CenterY + OFFSET_Y, image_height) / image_height) - 0.5f) * SCALEFACTOR_Y + 0.5f;
        return adjCoords;
    }


    public float[] imageNormalizedtoBlobLocation(float[] imgCoords, float image_width, float image_height) {
        float OFFSET_X = manualTransform[0];
        float OFFSET_Y = manualTransform[1];
        float SCALEFACTOR_X = manualTransform[2];
        float SCALEFACTOR_Y = manualTransform[3];

//        adjCoords[0] = ((Math.min(CenterX + OFFSET_X, image_width) / image_width) - 0.5f) * SCALEFACTOR_X + 0.5f;
        adjCoords[0] = Math.min(((((imgCoords[0] - 0.5f) / SCALEFACTOR_X) + 0.5f) * image_width) - OFFSET_X, image_width);
        adjCoords[1] = Math.min(((((imgCoords[1] - 0.5f) / SCALEFACTOR_Y) + 0.5f) * image_height) - OFFSET_Y, image_height);


//        Log.e("TUTIL", "ManualTransform" + Arrays.toString(manualTransform) + "-" + "ImgX: " + imgCoords[0] + " ImgY: " + imgCoords[1] + " --> " + adjCoords[0] + ", " + adjCoords[1]);
        return adjCoords.clone();
    }

    public float[] getImageToViewMatrix(Frame frame) {
        float[] frameTransform = new float[6];
        float[] uvTransform = new float[9];
        // XY pairs of coordinates in NDC space that constitute the origin and points along the two
        // principal axes.
        float[] ndcBasis = {0, 0, 1, 0, 0, 1};

        // Temporarily store the transformed points into outputTransform.
        frame.transformCoordinates2d(
                Coordinates2d.IMAGE_PIXELS,
                ndcBasis,
                Coordinates2d.VIEW,
                frameTransform);

        // Convert the transformed points into an affine transform and transpose it.
        float ndcOriginX = frameTransform[0];
        float ndcOriginY = frameTransform[1];
        uvTransform[0] = frameTransform[2] - ndcOriginX;
        uvTransform[1] = frameTransform[4] - ndcOriginX;
        uvTransform[2] = ndcOriginX;
        uvTransform[3] = frameTransform[3] - ndcOriginY;
        uvTransform[4] = frameTransform[5] - ndcOriginY;
        uvTransform[5] = ndcOriginY;
        uvTransform[6] = 0;
        uvTransform[7] = 0;
        uvTransform[8] = 1;

        return uvTransform;
    }

    /**
     * Part of stack to convert 3D coordinates to screenspace
     * https://stackoverflow.com/questions/49026297/convert-3d-world-arcore-anchor-pose-to-its-corresponding-2d-screen-coordinates
     *
     * @param modelmtx
     * @param viewmtx
     * @param projmtx
     * @param scaleFactor
     * @return
     */
    public static float[] calculateWorld2CameraMatrix(float[] modelmtx, float[] viewmtx, float[] projmtx, float scaleFactor) {

        float[] scaleMatrix = new float[16];
        float[] modelXscale = new float[16];
        float[] viewXmodelXscale = new float[16];
        float[] world2screenMatrix = new float[16];

        Matrix.setIdentityM(scaleMatrix, 0);
        scaleMatrix[0] = scaleFactor;
        scaleMatrix[5] = scaleFactor;
        scaleMatrix[10] = scaleFactor;

        Matrix.multiplyMM(modelXscale, 0, modelmtx, 0, scaleMatrix, 0);
        Matrix.multiplyMM(viewXmodelXscale, 0, viewmtx, 0, modelXscale, 0);
        Matrix.multiplyMM(world2screenMatrix, 0, projmtx, 0, viewXmodelXscale, 0);

        return world2screenMatrix;
    }


    /**
     * Part of stack to convert 3D coordinates to screenspace
     * https://stackoverflow.com/questions/49026297/convert-3d-world-arcore-anchor-pose-to-its-corresponding-2d-screen-coordinates
     *
     * @param screenWidth
     * @param screenHeight
     * @param world2cameraMatrix
     * @return
     */
    public static double[] world2Screen(int screenWidth, int screenHeight, float[] world2cameraMatrix) {
        float[] origin = {0f, 0f, 0f, 1f};
        float[] ndcCoord = new float[4];
        Matrix.multiplyMV(ndcCoord, 0, world2cameraMatrix, 0, origin, 0);

        ndcCoord[0] = ndcCoord[0] / ndcCoord[3];
        ndcCoord[1] = ndcCoord[1] / ndcCoord[3];

        double[] pos_2d = new double[]{0, 0};
        pos_2d[0] = screenWidth * ((ndcCoord[0] + 1.0) / 2.0);
        pos_2d[1] = screenHeight * ((1.0 - ndcCoord[1]) / 2.0);

        return pos_2d;
    }

    /**
     * Convert world space to screen space. See link for usage.
     * https://stackoverflow.com/questions/49026297/convert-3d-world-arcore-anchor-pose-to-its-corresponding-2d-screen-coordinates/49066308#49066308
     *
     * @param modelmtx
     * @param viewmtx
     * @param projmtx
     * @param scaleFactor
     * @param screenWidth
     * @param screenHeight
     * @return
     */
    public static double[] get_anchor_2d(float[] modelmtx, float[] viewmtx, float[] projmtx, float scaleFactor, int screenWidth, int screenHeight) {
        float[] world2screenMatrix = calculateWorld2CameraMatrix(modelmtx, viewmtx, projmtx, scaleFactor);
        return world2Screen(screenWidth, screenHeight, world2screenMatrix);
    }

}
