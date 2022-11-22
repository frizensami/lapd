package org.cyphy_lab.lapd.tof;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.util.Log;
import android.util.Range;
import android.util.SizeF;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.cyphy_lab.lapd.config.DepthCameraDimensions;

import java.util.Collections;
import java.util.List;

public class TOFCamera extends CameraDevice.StateCallback {
    private static final String TAG = TOFCamera.class.getSimpleName();

    private static final int FPS_MIN = 25;
    private static final int FPS_MAX = 25;

    // Final image width and height, inferred by phone model in constructor
    private static int IMAGE_WIDTH = -1;
    private static int IMAGE_HEIGHT = -1;

    private final Context context;
    private final CameraManager cameraManager;
    private final ImageReader previewReader;
    private CaptureRequest.Builder previewBuilder;
    public DepthFrameAvailableListener imageAvailableListener;

    // To allow for closing of the camera
    // NOTE: only allocated one camera device since onOpened doesn't know if we asked for RGB or ToF
    private CameraDevice currentCameraDevice;
    private CameraCaptureSession currentCameraCaptureSession;

    public TOFCamera(Context context, DepthFrameVisualizer depthFrameVisualizer) {
        this.context = context;
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        // Set camera dimensions based on our known map of model --> dimensions
        Log.d(TAG, android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
        DepthCameraDimensions depthCameraDimensions = new DepthCameraDimensions();
        IMAGE_WIDTH = depthCameraDimensions.getWidth();
        IMAGE_HEIGHT = depthCameraDimensions.getHeight();
        // find the multiplication we can do to keep the same aspect ratio in "fullscreen" mode
        //float aspect_ratio_factor = (float) screen_width / (float) IMAGE_WIDTH;
        float aspect_ratio_factor = 2;

        int fullscreen_width = (int) ((float) IMAGE_WIDTH * aspect_ratio_factor);
        int fullscreen_height = (int) ((float) IMAGE_HEIGHT * aspect_ratio_factor);
        imageAvailableListener = new DepthFrameAvailableListener(depthFrameVisualizer, IMAGE_WIDTH, IMAGE_HEIGHT);

        previewReader = ImageReader.newInstance(IMAGE_WIDTH,
                IMAGE_HEIGHT, ImageFormat.DEPTH16, 10);
        previewReader.setOnImageAvailableListener(imageAvailableListener, null);
    }

    // Open the front depth camera and start sending frames
    public void openFrontDepthCamera() {
        final String cameraId = getFrontDepthCameraID();
        openCamera(cameraId);
        Log.d(TAG, "Depth camera: " + cameraId);
    }


    // Open the front depth camera and start sending frames
    public void pauseOrStopFrontDepthCamera() {
        if (currentCameraCaptureSession != null) {
            try {
                currentCameraCaptureSession.abortCaptures();
            } catch (CameraAccessException e) {
                Log.e(TAG, "Pausing Camera has an Exception " + e);
            }
        }

        if (currentCameraDevice != null) {
            currentCameraDevice.close();
        }
    }


    // Open the front depth camera and start sending frames
    public void unPauseOrStopFrontDepthCamera() {
        openFrontDepthCamera();
    }

    private String getFrontDepthCameraID() {
        try {
            for (String camera : cameraManager.getCameraIdList()) {

                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(camera);
                final int[] capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                Log.e(TAG, camera);

                boolean facingFront = chars.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT;
                boolean depthCapable = false;
                for (int capability : capabilities) {

                    boolean capable = capability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT;
                    depthCapable = depthCapable || capable;

                    Log.i(TAG, "Capability: " + capability);

                }
                Log.e(TAG, "Depth capable" + depthCapable);
                Log.e(TAG, "Front Facing" + facingFront);
                if (depthCapable && !facingFront) {
                    // Note that the sensor size is much larger than the available capture size
                    SizeF sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);

                    Log.i(TAG, "Sensor size: " + sensorSize);

                    // Since sensor size doesn't actually match capture size and because it is
                    // reporting an extremely wide aspect ratio, this FoV is bogus
                    float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (focalLengths.length > 0) {
                        float focalLength = focalLengths[0];
                        double fov = 2 * Math.atan(sensorSize.getWidth() / (2 * focalLength));
                        Log.i(TAG, "Calculated FoV: " + fov);
                    }

                    return camera;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not initialize front depth camera", e);
        }
        return null;
    }

    private void openCamera(String cameraId) {
        try {
            int permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA);
            if (PackageManager.PERMISSION_GRANTED == permission) {
                cameraManager.openCamera(cameraId, this, null);
            } else {
                Log.e(TAG, "Permission not available to open ToF camera");
            }
        } catch (CameraAccessException | IllegalStateException | SecurityException e) {
            Log.e(TAG, "ToFCamera - Opening Camera has an Exception ", e);
        }
    }

    @Override
    public void onOpened(@NonNull CameraDevice camera) {
        try {
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);

            Range<Integer> fpsRange = new Range<>(FPS_MIN, FPS_MAX);
            previewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            previewBuilder.addTarget(previewReader.getSurface());

            List<Surface> targetSurfaces = Collections.singletonList(previewReader.getSurface());

            // Save the cameradevice we got so that we can close it later
            currentCameraDevice = camera;

            camera.createCaptureSession(targetSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            onCaptureSessionConfigured(session);
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "!!! Creating Capture Session failed due to internal error ");
                        }
                    }, null);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access the camera, but somehow this is (most of the time) not an actual error?", e);
            // System.exit(1);
        }
    }

    private void onCaptureSessionConfigured(@NonNull CameraCaptureSession session) {
        // Save the capture session we got so that we can close it later
        currentCameraCaptureSession = session;

        Log.i(TAG, "Capture Session created");
        previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        // previewBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        // previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        try {
            session.setRepeatingRequest(previewBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access camera in onCaptureSessionConfigured for ToFCamera", e);
        }
    }


    @Override
    public void onDisconnected(@NonNull CameraDevice camera) {

    }

    @Override
    public void onError(@NonNull CameraDevice camera, int error) {

    }


    public int getImageWidth() {
        return IMAGE_WIDTH;
    }

    public int getImageHeight() {
        return IMAGE_HEIGHT;
    }
}
