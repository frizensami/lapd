package org.cyphy_lab.lapd.core;

import org.opencv.core.Point;

import java.nio.ByteBuffer;
import java.util.List;

public class Keypoint {
    public Point location;
    public float size;
    public float width;
    public float height;
    public Point topLeft;
    public int blob_number;
    public double depthMetres;
    public float screenCoords_x = -1;
    public float screenCoords_y = -1;

    /*
    Byte array structure
    location.x: double
    location.y: double
    size: float
    width: float
    height: float
    topLeft.x: double
    topLeft.y: double
    blob_number: int
    depthMetres: double
    screenCoords_x: float
    screenCoords_y: float

    Overall: 5 doubles, 5 floats, 1 int
     */
    public static final int byteBufferSize = 5 * Double.BYTES + 5 * Float.BYTES + Integer.BYTES;

    public ByteBuffer toByteBuffer() {
        ByteBuffer b = ByteBuffer.allocate(byteBufferSize);
        b.putDouble(location.x);
        b.putDouble(location.y);
        b.putFloat(size);
        b.putFloat(width);
        b.putFloat(height);
        b.putDouble(topLeft.x);
        b.putDouble(topLeft.y);
        b.putInt(blob_number);
        b.putDouble(depthMetres);
        b.putFloat(screenCoords_x);
        b.putFloat(screenCoords_y);
        b.rewind();
        return b;
    }

    public static byte[] keypointsToByteArray(List<Keypoint> keypoints) {
        ByteBuffer bb = ByteBuffer.allocate(Keypoint.byteBufferSize * keypoints.size());
        for (Keypoint keypoint : keypoints) {
            bb.put(keypoint.toByteBuffer());
        }
        return bb.array();
    }
}