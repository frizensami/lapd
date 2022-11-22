package org.cyphy_lab.lapd.arcore;

import org.cyphy_lab.lapd.config.PhoneLidarConfig;

import static org.cyphy_lab.lapd.util.VectorUtils.vecDistance;

public class RelativeAnchor {
    public float[] relativeAvgDrawPos;
    public float[] initialPos;
    public int numPointsForPos = 1;
    public float[] color;
    public boolean lockedOn = false;
    public int numStrikes = 0; // How many
    public boolean isDead = false;

    public RelativeAnchor(float[] relativeAvgPos, float[] color) {
        this.relativeAvgDrawPos = relativeAvgPos;
        this.initialPos = relativeAvgPos.clone();
        this.color = color;
    }

    /**
     * Receives a new detected blob location near it, calculates new centroid
     * This method exists because of this situation:
     * 1. We get a random blob spotted at a location NEAR a camera
     * 2. We then spot the real camera blobs nearby
     * 3. However, the first location3D is what we associate all the camera blob data to
     * 4. Result: correctly predicted camera but at the wrong visual position
     * <p>
     * Problems:
     * - If we update the new average position unboundedly, the point will tend to shift far away from initial pos (lose info)
     *
     * @param newPos
     */
    public void computeNewRelativePos(float[] newPos) {
        // Keep old position around to see if we exceed a set distance from the initial pos
        float[] beforeUpdatePos = this.relativeAvgDrawPos.clone();
        // Compute new position
        for (int i = 0; i < 3; i++) {
            // Average --> sum
            this.relativeAvgDrawPos[i] *= numPointsForPos;
            // Add new point
            this.relativeAvgDrawPos[i] += newPos[i];
            // Average it out again
            this.relativeAvgDrawPos[i] /= (this.numPointsForPos + 1);
        }
        // Make sure the distance is not too far from the old pos
        double distance = vecDistance(relativeAvgDrawPos, beforeUpdatePos);

        if (distance <= (PhoneLidarConfig.ANCHOR_STICKY_RADIUS / 2)) {
            // Still within original sticky radius, keep the relative pose
            this.numPointsForPos++;
        } else {
            // Too far, reset pos
            this.relativeAvgDrawPos = beforeUpdatePos;
        }
    }
}
