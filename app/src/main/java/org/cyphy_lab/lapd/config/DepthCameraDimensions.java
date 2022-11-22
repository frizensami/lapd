package org.cyphy_lab.lapd.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Gets the default sensor resolution we inferred for each model
 */
public class DepthCameraDimensions {
    // Since we don't have tuples..
    public static class CameraDimension {
        public int width;
        public int height;

        public CameraDimension(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }


    // Where we will store the final inferred dimension
    private CameraDimension currentDefaultDimension;

    /**
     * Set camera dimensions from our map and current build string
     */
    public DepthCameraDimensions() {
        String deviceString = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
        this.currentDefaultDimension = PhoneLidarConfig.cameraDimensionMap.get(deviceString);
        if (this.currentDefaultDimension == null) {
            // Set to QVGA default just in case
            this.currentDefaultDimension = new CameraDimension(320, 240);
        }
    }

    public int getWidth() {
        return currentDefaultDimension.width;
    }

    public int getHeight() {
        return currentDefaultDimension.height;
    }
}
