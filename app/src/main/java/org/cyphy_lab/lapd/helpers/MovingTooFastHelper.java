package org.cyphy_lab.lapd.helpers;

/**
 * Stores a camera pose and then checks movement speed after some number of seconds
 */
public class MovingTooFastHelper {
    private final float MAX_SPEED;
    private final float INTEGRATION_TIME_MINIMUM_SEC;
    private boolean isMovingTooFast = false;

    private long referenceTranslationTimeMillis;
    private float[] referenceTranslation;

    /**
     * Sets the maximum allowable motion speed and over how long we should calculate the user's average speed
     *
     * @param maxSpeed               Maximum speed in m/s
     * @param integrationTimeSeconds How long to average the user's movement speed over
     */
    public MovingTooFastHelper(float maxSpeed, float integrationTimeSeconds) {
        this.MAX_SPEED = maxSpeed;
        this.INTEGRATION_TIME_MINIMUM_SEC = integrationTimeSeconds;
    }

    public void updateTranslation(float[] translation) {
        if (isMovingTooFast) return; // Require a manual reset if triggered
        // First initialization of the reference point
        if (referenceTranslation == null) {
            referenceTranslation = translation;
            referenceTranslationTimeMillis = System.currentTimeMillis();
            return;
        }

        // We have a reference already, check if we need to calculate speed yet
        double timeElapsedSec = (System.currentTimeMillis() - referenceTranslationTimeMillis) / 1000.0;
        if (timeElapsedSec > INTEGRATION_TIME_MINIMUM_SEC) {
            // We need to compute speed now
//            Log.e("Moving too fast helper", "Time elapsed");
            double distance = Math.sqrt(Math.pow(referenceTranslation[0] - translation[0], 2)
                    + Math.pow(referenceTranslation[1] - translation[1], 2)
                    + Math.pow(referenceTranslation[2] - translation[2], 2));
            double speedMetersPerSecond = distance / timeElapsedSec;
//            Log.e("Moving too fast helper", "Speed m/s: " + speedMetersPerSecond);
            // Check speed
            if (speedMetersPerSecond > this.MAX_SPEED) {
                isMovingTooFast = true;
            } else {
                // We haven't over-sped, reset the reference
                referenceTranslation = translation;
                referenceTranslationTimeMillis = System.currentTimeMillis();
            }
        }
    }

    public boolean isMovingTooFast() {
        return isMovingTooFast;
    }

    public void resetMovingTooFast() {
        isMovingTooFast = false;
        referenceTranslation = null;
    }
}
