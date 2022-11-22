package org.cyphy_lab.lapd.core;

import java.util.concurrent.locks.ReentrantLock;

public class DataBuffers {
    public static byte[] depth16FromToFSensorInternal;
    public static byte[] depth16FromToFSensorForRGB;

    public static byte[] depth16FromToFSensorPublished;

    public static final ReentrantLock depthAndBlobDataSynchronizerLock = new ReentrantLock(true);
    public static final ReentrantLock dataBufferPublisherLock = new ReentrantLock(true);


    public static void initRawMaskBuffer(int size) {
        depth16FromToFSensorPublished = new byte[2 * size];
        depth16FromToFSensorForRGB = new byte[2 * size];
        depth16FromToFSensorInternal = new byte[2 * size];


    }
}
