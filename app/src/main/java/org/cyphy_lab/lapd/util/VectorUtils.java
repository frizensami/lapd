package org.cyphy_lab.lapd.util;

public class VectorUtils {

    ///////////////////////////////////
    ////// vector util functions //////
    ///////////////////////////////////
    // Function to find
    // cross product of two vector array.

    public static float[] getQuaternionBetween(float[] A, float[] B) {
        float[] quat = new float[4];
        float[] crossp = crossProduct(A, B);
        // Set XYZ
        System.arraycopy(crossp, 0, quat, 0, 3);
        // Set W
        quat[3] = (float) Math.sqrt(dotProduct(A, A) * dotProduct(B, B)) + dotProduct(A, B);
        return quat;
    }

    public static float[] crossProduct(float[] vect_A, float[] vect_B) {
        float[] cross_P = new float[4];

        cross_P[0] = vect_A[1] * vect_B[2]
                - vect_A[2] * vect_B[1];
        cross_P[1] = vect_A[2] * vect_B[0]
                - vect_A[0] * vect_B[2];
        cross_P[2] = vect_A[0] * vect_B[1]
                - vect_A[1] * vect_B[0];
        return cross_P;
    }

    public static float vecLength(float[] a) {
        return (float) Math.sqrt(dotProduct(a, a));
    }

    public static float dotProduct(float[] a, float[] b) {
        float result = 0.0f;
        for (int i = 0; i < 3; i++) {
            result += a[i] * b[i];
        }
        return result;
    }

    public static float[] normalize(float[] v) {
        float[] result = new float[3];
        float distance = vecLength(v);
        for (int i = 0; i < 3; i++) {
            result[i] = v[i] / distance;
        }
        return result;
    }

    public static float[] subtract(float[] a, float[] b) {
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            result[i] = a[i] - b[i];
        }
        return result;
    }

    public static float[] scale(float[] v, float scale) {
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            result[i] = v[i] * scale;
        }
        return result;
    }

    public static float[] add3d(float[] v1, float[] v2) {
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            result[i] = v1[i] + v2[i];
        }
        return result;
    }

    public static float calculateProjectedDistanceOnPlane(float[] newPosition, float[] oldPosition) {
        float x = newPosition[0] - oldPosition[0];
        float y = newPosition[1] - oldPosition[1];
        return (float) Math.sqrt(x * x + y * y);
    }

    public static double vecDistance(float[] v1, float[] v2) {
        return Math.sqrt(
                Math.pow(v1[0] - v2[0], 2)
                        + Math.pow(v1[1] - v2[1], 2)
                        + Math.pow(v1[2] - v2[2], 2)
        );
    }


    public static double vecDistanceWithoutZ(double[] v1, double[] v2) {
        return Math.sqrt(
                Math.pow(v1[0] - v2[0], 2)
                        + Math.pow(v1[1] - v2[1], 2)
        );
    }

    public static boolean pointInside2D(float x1, float y1, float x2, float y2, float xp, float yp) {
        return x1 <= xp && xp <= x2 && y1 <= yp && yp < y2;
    }
}
