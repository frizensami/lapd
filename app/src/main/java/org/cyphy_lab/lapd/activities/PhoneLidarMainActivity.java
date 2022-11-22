
/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cyphy_lab.lapd.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.cyphy_lab.lapd.R;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Config.InstantPlacementMode;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.InstantPlacementPoint;
import com.google.ar.core.InstantPlacementPoint.TrackingMethod;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import org.cyphy_lab.lapd.arcore.ObjectBbox;
import org.cyphy_lab.lapd.arcore.RelativeAnchor;
import org.cyphy_lab.lapd.arcore.TransformationUtil;
import org.cyphy_lab.lapd.config.PhoneLidarConfig;
import org.cyphy_lab.lapd.core.CVManager;
import org.cyphy_lab.lapd.core.CurrentState;
import org.cyphy_lab.lapd.core.DataBuffers;
import org.cyphy_lab.lapd.core.EntropyGridSystem;
import org.cyphy_lab.lapd.core.FilterModel;
import org.cyphy_lab.lapd.core.Keypoint;
import org.cyphy_lab.lapd.core.MetricsManager;
import org.cyphy_lab.lapd.helpers.CameraPermissionHelper;
import org.cyphy_lab.lapd.helpers.DepthSettingsHelper;
import org.cyphy_lab.lapd.helpers.DisplayRotationHelper;
import org.cyphy_lab.lapd.helpers.FullScreenHelper;
import org.cyphy_lab.lapd.helpers.InstantPlacementSettingsHelper;
import org.cyphy_lab.lapd.helpers.MovingTooFastHelper;
import org.cyphy_lab.lapd.helpers.SnackbarHelper;
import org.cyphy_lab.lapd.helpers.TapHelper;
import org.cyphy_lab.lapd.helpers.TrackingStateHelper;
import org.cyphy_lab.lapd.renderer.Mesh;
import org.cyphy_lab.lapd.renderer.SampleRender;
import org.cyphy_lab.lapd.renderer.Shader;
import org.cyphy_lab.lapd.renderer.Texture;
import org.cyphy_lab.lapd.renderer.VertexBuffer;
import org.cyphy_lab.lapd.renderer.arcore.BackgroundRenderer;
import org.cyphy_lab.lapd.renderer.arcore.PlaneRenderer;
import org.cyphy_lab.lapd.tof.DepthFrameVisualizer;
import org.cyphy_lab.lapd.tof.TOFCamera;
import org.cyphy_lab.lapd.util.ColorArrays;
import org.cyphy_lab.lapd.util.VectorUtils;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


public class PhoneLidarMainActivity extends AppCompatActivity implements SampleRender.Renderer, DepthFrameVisualizer {

    /****************************
     * CLASS MEMBERS
     ****************************/

    private static final String TAG = PhoneLidarMainActivity.class.getSimpleName();

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;
    private SampleRender render;

    private Texture depthTexture;
    private boolean calculateUVTransform = true;
    private PlaneRenderer planeRenderer;
    private BackgroundRenderer backgroundRenderer;
    private boolean hasSetTextureNames = false;


    private final DepthSettingsHelper depthSettings = new DepthSettingsHelper();
    private final boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];

    private final InstantPlacementSettingsHelper instantPlacementSettings = new InstantPlacementSettingsHelper();
    private final boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];

    private final boolean[] autoFocusSettingsMenuDialogCheckboxes = new boolean[1];
    private boolean isAutoFocusEnabled = true;

    private final boolean[] filterSettingsMenuDialogCheckboxes = new boolean[6];

    private final boolean[] filter3DSettings3MenuDialogCheckboxes = new boolean[3];
    private boolean isRemovingSmallAnchors = false; // We should remove them at the end of scans, not periodically
    private boolean isDecayingAnchors = false;
    private boolean isFOVFiltering = true;

    private final boolean[] uiSettingsMenuDialogCheckboxes = new boolean[22];
    private boolean isDepthOverlayEnabled = false;
    private boolean isShowPlanesEnabled = false;
    private boolean isARCoreDisplayEnabled = true;
    private boolean isConfidenceFullscreen = false;
    private boolean isConfidenceInsetEnabled = true;
    private boolean isHeatmapInsetEnabled = false;
    private boolean isPointCloudVisualizationEnabled = false;
    private boolean isTooCloseVizualizationEnabled = false;
    private boolean isLockingVizualizationEnabled = false;
    private boolean isPhoneCoordinatesEnabled = true;
    private boolean isMovingTooFastViewEnabled = false;
    private boolean isShowAnchorsBelowMinScore = true;
    private boolean isColorHighEntropyBlobsBeforeMinimapCompleteEnabled = true;
    private boolean isHideAnchorsWhileMinimapInProgress = false;
    private boolean isShowIndividualSquareProgressBar = false;
    private boolean isShowAllFovFilterBlobs = false;
    private boolean isShowMLThresholdSeekbar = true;
    private boolean isWaitToCreateReferenceAnchors = false;
    private boolean isUpdateAnchorPositionOverTime = true;
    private boolean isDisplayAllEntropyPoses = false;
    private boolean isDisplayLockedEntropyPoses = true;
    private boolean isShowAllScannedDistancesAsGreen = true;

    // Point Cloud
    private static final String POINT_CLOUD_VERTEX_SHADER_NAME = "shaders/point_cloud.vert";
    private static final String POINT_CLOUD_FRAGMENT_SHADER_NAME = "shaders/point_cloud.frag";
    private VertexBuffer pointCloudVertexBuffer;
    private Mesh pointCloudMesh;
    private Shader pointCloudShader;
    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
    private long lastPointCloudTimestamp = 0;
    // Point cloud point params (color and size)
    private static final float POINTCLOUD_POINT_COLOR_R = 31.0f / 255.0f;
    private static final float POINTCLOUD_POINT_COLOR_G = 188.0f / 255.0f;
    private static final float POINTCLOUD_POINT_COLOR_B = 210.0f / 255.0f;
    private static final float POINTCLOUD_POINT_COLOR_A = 1.0f;
    private static final float POINTCLOUD_POINT_SIZE = 10.0f;

    // Virtual object
    private static final String AMBIENT_INTENSITY_VERTEX_SHADER_NAME =
            "shaders/ambient_intensity.vert";
    private static final String AMBIENT_INTENSITY_FRAGMENT_SHADER_NAME =
            "shaders/ambient_intensity.frag";
    // Object models and textures for the actual placed object
    private static final String VIRTUAL_OBJECT_TEXTURE_NAME = "models/andy.png";
    private static final String VIRTUAL_OBJECT_MESH_NAME = "models/sphere.obj";
    private static final String VIRTUAL_CYLINDER_MESH_NAME = "models/cylinder.obj";
    private static final String REFERENCE_OBJECT_MESH_NAME = "models/cube.obj";
    // Note: the last component must be zero to avoid applying the translational part of the matrix.
    private static final float[] LIGHT_DIRECTION = {0.250f, 0.866f, 0.433f, 0.0f};
    private Mesh virtualCylinderMesh;
    private Mesh sphereObjectMesh;
    private Mesh referenceObjectMesh;
    private Shader virtualObjectShader;
    private Shader referenceObjectShader;
    private Shader virtualObjectDepthShader;

    // Line-drawing rotation axis vector
    private static final float[] LINE_ROTATION_AXIS = new float[]{0, 1, 0};

    // For color correction
    private final float[] colorCorrectionRgba = new float[4];

    // OURS
    private TOFCamera tofCamera;
    private android.graphics.Matrix confidenceScaledBitmapTransform;
    private android.graphics.Matrix heatmapBitmapTransform;
    private TextureView depthDataView;
    private TextureView depthDataViewFull;
    private CVManager mcvManager;
    private MetricsManager mMetricsManager;
    private TextureView bboxOverlay;
    private TextureView entropyGridView;
    private ConstraintLayout tooCloseWarningLayout;
    private TextView tooCloseTextView;
    private ConstraintLayout notVisibleWarningLayout;
    private ConstraintLayout movingTooFastWarningLayout;
    private ConstraintLayout lowFPSWarningLayout;
    private ConstraintLayout noToFWarningLayout;
    private ConstraintLayout outsideMapWarningLayout;
    private TextView cameraPoseTextView;
    private TransformationUtil transformationUtil;
    private Button toggleViewButton;

    // State management
    private CurrentState currentState = CurrentState.FINDING_REFERENCE_ANCHOR;
    private TextView statusTextView;

    // Static painters (avoid new in Paints)
    private static final Paint renderDepthPainter = new Paint();
    private static final Paint bboxPainter = new Paint();
    private static final Paint bboxPainterGreen = new Paint();

    // Vibration
    private Vibrator vibrator;

    static {
        renderDepthPainter.setAlpha(100);
        bboxPainter.setColor(Color.RED);
        bboxPainter.setStyle(Paint.Style.STROKE);
        bboxPainter.setStrokeWidth(10);


        bboxPainterGreen.setColor(Color.GREEN);
        bboxPainterGreen.setStyle(Paint.Style.STROKE);
        bboxPainterGreen.setStrokeWidth(10);
    }

    // Toast management - one toast so that we can cancel toasts as necessary
    private Toast myToast;

    // Moving speed system
    MovingTooFastHelper movingTooFastHelper = new MovingTooFastHelper(PhoneLidarConfig.MAX_MOVEMENT_SPEED_METERS_PER_SEC, PhoneLidarConfig.MAX_MOVEMENT_SPEED_AVERAGING_TIME_SEC);

    // Entropy Grid system
    private EntropyGridSystem entropyGridSystem = null;
    ImageView rightArrowView;
    ImageView leftArrowView;
    ImageView upArrowView;
    ImageView downArrowView;

    // Saturation system
    private ProgressBar saturationProgressBar;
    private TextureView distScanProgressBarTextureView;
    private ConstraintLayout distScanProgressBarLayout;
    private TextView distScanCloseEnoughTextView;
    private TextView distScanFarEnoughTextView;
    private float latest_tof_dist = 0.0f;
    private float latest_z_dist = 0.0f;

    // OURS
    private static final float TAN10DEG = 0.1763269807f;
    private static final float TAN5DEG = 0.08748866352f;

    // OURS: To save photo of current state
    private Button resetButton;
    private Button infoButton;
    private ConstraintLayout tooltipsLayout;
    private ProgressBar scanProgressBar;

    // Changing ML parameters easily
    private SeekBar mlSeekBar;
    private TextView mlSeekBarTextView;

    private static final AtomicInteger frameCounter = new AtomicInteger(0);

    private int GLSURFACE_HEIGHT = 0;
    private int GLSURFACE_WIDTH = 0;

    // Size multiplier for the bounding circles
    private final float BOUNDING_BOX_DISPLAY_SCALE = 3.0f;

    private float[] imageToViewTransformationMatrix = new float[9];
    private boolean calculateTransformationMatrix = true;
    private Frame frame;

    private Pose lastCameraPose = null;
    private Pose currentCameraPose;

    private static boolean showTooltips = false;


    // Open model
    FilterModel filterModel;

    // FPS
    private int fpsFrameCounter;
    private long firstFrameTimeMillis;
    private double averageFps;
    private long lastFrameTime;
    private final int FPS_LOOKBACK_FRAMES = 60;

    // Point cloud status
    private int latestNumPointCloudPoints;
    private float latestAvgPointCloudConf;

    // Data Collection
    private boolean DC_isSetPose = false;
    private Pose DC_VectorNormal;

    /****************************
     * END CLASS MEMBERS
     ****************************/


    /**********************************************
     * Android Lifecycle (create, resume, etc)
     **********************************************/

    @Override
    @SuppressLint("NewApi")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceview);

        // We will update our screen height and width (actually surface height and width) properly later
        // This is a sane default for now
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        GLSURFACE_HEIGHT = displayMetrics.heightPixels;
        GLSURFACE_WIDTH = displayMetrics.widthPixels;

        Log.i(TAG, "displayMetrics:" + displayMetrics.toString());
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        // Set up toast management
        myToast = Toast.makeText(getApplicationContext(), "", Toast.LENGTH_SHORT);

        // Set up touch listener.onSurfaceChanged
        tapHelper = new TapHelper(/*context=*/ this);
        surfaceView.setOnTouchListener(tapHelper);

        // Create ML model
        filterModel = new FilterModel(this);
        FilterModel.runInferenceTest();

        // Set up renderer.
        render = new SampleRender(surfaceView, this, getAssets());

        installRequested = false;
        calculateUVTransform = true;

        depthSettings.onCreate(this);
        instantPlacementSettings.onCreate(this);

        // Set ML sliders
        mlSeekBar = findViewById(R.id.seekbarMLThreshold);
        mlSeekBarTextView = findViewById(R.id.seekbarMLThresholdTextView);
        mlSeekBar.setMax(100);
        mlSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float newProbability = ((float) progress) / 100.0f;
                PhoneLidarConfig.MIN_CAMERA_PREDICTION_PROBABILITY = newProbability;
                mlSeekBarTextView.setText(String.valueOf(newProbability));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        updateMLSeekBarValueFromSettings();


        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(
                v -> {
                    PopupMenu popup = new PopupMenu(PhoneLidarMainActivity.this, v);
                    popup.setOnMenuItemClickListener(PhoneLidarMainActivity.this::settingsMenuClick);
                    popup.inflate(R.menu.settings_menu);
                    popup.show();
                });

        // OURS - draw ToF Camera data on depthDataView (a TextureView)
        depthDataView = findViewById(R.id.depthData);
        depthDataViewFull = findViewById(R.id.depthDataFull);
        tooCloseWarningLayout = findViewById(R.id.tooCloseWarningLayout);
        notVisibleWarningLayout = findViewById(R.id.notVisibleWarningLayout);
        tooCloseTextView = findViewById(R.id.tooCloseTextView);
        movingTooFastWarningLayout = findViewById(R.id.tooFastWarningLayout);
        outsideMapWarningLayout = findViewById(R.id.outsideMapLayout);
        lowFPSWarningLayout = findViewById(R.id.lowFPSLayout);
        noToFWarningLayout = findViewById(R.id.noToFDataLayout);
        cameraPoseTextView = findViewById(R.id.cameraPoseTextview);
        toggleViewButton = findViewById(R.id.toggleViewMode);
        toggleViewButton.setOnClickListener((__) -> {
            toggleView();
        });

        // Vibration
        vibrator = (Vibrator) PhoneLidarMainActivity.this.getSystemService(Context.VIBRATOR_SERVICE);

        // Entropy grid system and visualization
        entropyGridView = findViewById(R.id.entropyGrid);
        rightArrowView = findViewById(R.id.rightArrow);
        leftArrowView = findViewById(R.id.leftArrow);
        upArrowView = findViewById(R.id.upArrow);
        downArrowView = findViewById(R.id.downArrow);

        // Saturation
        saturationProgressBar = findViewById(R.id.saturationProgressBar);
        saturationProgressBar.setMax(100);
        distScanProgressBarTextureView = findViewById(R.id.distScanProgressBarTexture);
        distScanProgressBarLayout = findViewById(R.id.distScanProgressBarLayout);
        distScanCloseEnoughTextView = findViewById(R.id.distScanCloseEnoughTextView);
        distScanFarEnoughTextView = findViewById(R.id.distScanFarEnoughTextView);

        // OURS - OpenCV code
        mcvManager = new CVManager(this);
        mcvManager.setSettingsArrayFromFilterSettings(filterSettingsMenuDialogCheckboxes);

        transformationUtil = new TransformationUtil(this);

        // Camera and overlays
        bboxOverlay = findViewById(R.id.bboxOverlay);
        tofCamera = new TOFCamera(this, this);
        // tofCamera.openFrontDepthCamera();

        // Status management
        statusTextView = findViewById(R.id.statusTextView);


        // OURS - for saving metrics
        mMetricsManager = new MetricsManager();

        resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(view -> {
            resetAnchorHeatmap();
        });
        resetButton.setOnLongClickListener(view -> {
            resetSystem();
            return true; // Long click consumed
        });


        infoButton = findViewById(R.id.infoButton);
        tooltipsLayout = findViewById(R.id.tooltipsLayout);
        tooltipsLayout.setVisibility(View.INVISIBLE);
        infoButton.setOnClickListener(view -> {
            showTooltips = !showTooltips;
            if (showTooltips) {
                tooltipsLayout.setVisibility(View.VISIBLE);
            } else {
                tooltipsLayout.setVisibility(View.INVISIBLE);
            }
        });

        scanProgressBar = findViewById(R.id.scanningProgressBar);
        scanProgressBar.setVisibility(View.INVISIBLE);
    }

    private void updateMLSeekBarValueFromSettings() {
        mlSeekBar.setProgress((int) Math.round(PhoneLidarConfig.MIN_CAMERA_PREDICTION_PROBABILITY * 100.0));
    }

    private void toggleView() {
        // Toggle fullscreen confidence based on toggle button
        isConfidenceFullscreen = !isConfidenceFullscreen;
        isConfidenceInsetEnabled = !isConfidenceInsetEnabled;
        resetSettingsMenuDialogCheckboxes();
        applySettingsMenuDialogCheckboxes();
    }

    private void resetSystem() {
        showToast("Reset entire anchor system");
        // vibrate
        vibrator.vibrate(30);
        // clear reference anchors
        clearReferenceAnchorAndState();
        // This means we should reset our state machine
        currentState = CurrentState.FINDING_REFERENCE_ANCHOR;
        resetAnchorHeatmap();
        // set to non-instant placement mode
        instantPlacementSettings.setInstantPlacementEnabled(false);
    }


    private void resetAnchorHeatmap() {
        showToast("Reset anchor heatmap");
        // vibrate
        vibrator.vibrate(30);
        // clear anchor heatmap
        anchor_heatmap.clear();
        // clear all bounding boxes as well, no longer focusing on an object
        // This requires a state reset back to waiting for user bbox
        clearBboxAnchorsAndStateAndVisibility();
        // Reset cvManager's rgb-roi since we are no longer tracking an object
        mcvManager.resetRgbRoi();
        // also reset the entropy grid system
        if (entropyGridSystem != null) {
            entropyGridSystem.clearEntropyGrid();
            entropyGridSystem.setIsStarted(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            session.close();
            session = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create the ARCore session (!! Important !!)
                session = new Session(/* context= */ this);
                CameraConfigFilter filter = new CameraConfigFilter(session);
                filter.setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE));
                List<CameraConfig> cameraConfigList = session.getSupportedCameraConfigs(filter);
                session.setCameraConfig(cameraConfigList.get(0));

            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }
            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }

        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            configureSession();
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
        tofCamera.unPauseOrStopFrontDepthCamera();
        mcvManager.onResume(getApplicationContext());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
            tofCamera.pauseOrStopFrontDepthCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            showToast("Camera permission is needed to run this application");
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Some android fullscreen helper from base ARocre
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(SampleRender render) {
        // Prepare the rendering objects. This involves reading shaders and 3D model files, so may throw
        // an IOException.
        try {
            depthTexture = new Texture(render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE);
            planeRenderer = new PlaneRenderer(render);
            backgroundRenderer = new BackgroundRenderer(render, depthTexture);

            // Point cloud
            pointCloudShader =
                    Shader.createFromAssets(
                            render,
                            POINT_CLOUD_VERTEX_SHADER_NAME,
                            POINT_CLOUD_FRAGMENT_SHADER_NAME,
                            /*defines=*/ null)
                            .set4("u_Color", new float[]{
                                    POINTCLOUD_POINT_COLOR_R, POINTCLOUD_POINT_COLOR_G, POINTCLOUD_POINT_COLOR_B, POINTCLOUD_POINT_COLOR_A
                            })
                            .set1("u_PointSize", POINTCLOUD_POINT_SIZE);


            // four entries per vertex: X, Y, Z, confidence
            pointCloudVertexBuffer =
                    new VertexBuffer(render, /*numberOfEntriesPerVertex=*/ 4, /*entries=*/ null);
            final VertexBuffer[] pointCloudVertexBuffers = {pointCloudVertexBuffer};
            pointCloudMesh =
                    new Mesh(
                            render, Mesh.PrimitiveMode.POINTS, /*indexBuffer=*/ null, pointCloudVertexBuffers);

            // Virtual object to render (Andy the android)
            Texture virtualObjectTexture =
                    Texture.createFromAsset(render, VIRTUAL_OBJECT_TEXTURE_NAME, Texture.WrapMode.CLAMP_TO_EDGE);
            sphereObjectMesh = Mesh.createFromAsset(render, VIRTUAL_OBJECT_MESH_NAME);
            virtualCylinderMesh = Mesh.createFromAsset(render, VIRTUAL_CYLINDER_MESH_NAME);
            referenceObjectMesh = Mesh.createFromAsset(render, REFERENCE_OBJECT_MESH_NAME);

            virtualObjectShader = createVirtualObjectShader(render, virtualObjectTexture, false);
            referenceObjectShader = createVirtualObjectShader(render, virtualObjectTexture, false);
            virtualObjectDepthShader =
                    createVirtualObjectShader(render, virtualObjectTexture, /*use_depth_for_occlusion=*/ true)
                            .setTexture("u_DepthTexture", depthTexture);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        Log.d(TAG, "GLES SurfaceView Width: " + surfaceView.getWidth() + " Height: " + surfaceView.getHeight());
        displayRotationHelper.onSurfaceChanged(width, height);
    }


    /**********************************************
     * END Android Lifecycle (create, resume, etc)
     **********************************************/


    /**********************************************
     * ToF-related callback and main draw
     **********************************************/

    /**
     * Main callback from ToF camera to this activity for display
     */
    @Override
    public void onRawDataAvailable(Bitmap bitmap, Mat depthOverlay) {
        if (Core.countNonZero(depthOverlay) == 0) {
            Log.e(TAG, "All 0 data from ToF sensor");
            this.runOnUiThread(() -> noToFWarningLayout.setVisibility(View.VISIBLE));
        } else {
            this.runOnUiThread(() -> noToFWarningLayout.setVisibility(View.INVISIBLE));
        }

        if (isConfidenceFullscreen) {
            renderDepthBitmapToTextureView(bitmap, depthDataViewFull);
        } else {
            renderDepthBitmapToTextureView(bitmap, depthDataView);
        }
        displayTooCloseWarningIfNecessary(depthOverlay);
    }

    public void restartApplication() {
        Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(
                getBaseContext().getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onDrawFrame(SampleRender render) {
        if (session == null) {
            return;
        }

        // Appears that our surfaceview can change height and width (likely due to title bars)
        // Update it on each frame (but likely overkill)
        GLSURFACE_HEIGHT = surfaceView.getHeight();
        GLSURFACE_WIDTH = surfaceView.getWidth();

        if (!hasSetTextureNames) {
            session.setCameraTextureNames(new int[]{backgroundRenderer.getTextureId()});
            hasSetTextureNames = true;
        }

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        // update frameCounter
        frameCounter.incrementAndGet();

        // report metrics
        logMetricsPeriodically();

        // Check if the ToF camera's FPS is too low and display warning if so
        checkFPSTooLow(mcvManager.averageFps);

        // Calculate our own GLThread FPS
        updateFps();

        try {
            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            frame = session.update();
            Camera camera = frame.getCamera();

            // Get projection matrix.
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

            // Get camera matrix and draw. camera.getViewMatrix is essentially camera.getDisplayOrientedPose().inverse()
            camera.getViewMatrix(viewMatrix, 0);

            // update camera pose if null
            if (lastCameraPose == null) {
                lastCameraPose = camera.getPose();
            }

            // check if the camera is moving horizontally or vertically (gaining entropy)
            currentCameraPose = camera.getPose();
            float[] lastCameraPosition = lastCameraPose.getTranslation();
            float[] currentCameraPosition = currentCameraPose.getTranslation();
            float movingDistance = VectorUtils.calculateProjectedDistanceOnPlane(currentCameraPosition, lastCameraPosition);
            boolean isPhoneMoving = movingDistance > PhoneLidarConfig.FOV_EPSILON_DISTANCE_FOR_NEW_POSE;
            // set lastCameraPose
            lastCameraPose = currentCameraPose;

            // Calculate UV and Image to Screen transformation matrices if necessary
            calculateTransformationMatrices();

            // Update the current tracking state based on the pointcloud
            updatePointCloudState();

            /// STOP: BE CAREFUL WHEN UPDATING ANYTHING INSIDE THE LOCKING AREA BELOW

            // ACQUIRE LOCK 1
            // We stop the save file button from updating the save file until we update all data
            // This lock is not so bad. Contention currently only happens when we use the "Save" button.
            DataBuffers.dataBufferPublisherLock.lock();

            // MAIN BLOB PROCESSING
            // Initialize reference anchors for the first few frames
            if (currentState == CurrentState.FINDING_REFERENCE_ANCHOR) {
                boolean isNewAnchorCreated = createReferenceAnchors(frame, session);
            } else {
                // If we have the reference anchors already, we set instant placement mode
                instantPlacementSettings.setInstantPlacementEnabled(true);

                // Handle one tap per frame, right now used for focusing on a blob's reflection sources.
                handleTap(frame, camera);

                // Handle user dragging a new bbox around an object.
                handleBboxSelection();

                // If we have a bbox selection, calculate the ideal distance to stand at.
                runDepthScan(camera);

                // Update bbox distance and the "too close / too far" scan information
                updateBboxDistanceAfterDepthScan(camera);

                // filter heatmap
                filterExistingAnchorHeatmap();

                List<Keypoint> blobs;
                List<Keypoint> fov_blobs;

                // ACQUIRE LOCK 2
                // DepthFrameAvailableListener cannot update the published final blobs and depth16 for RGB during this
                // CRITICAL SECTION - limits the cvmanager loop if this takes too long.
                DataBuffers.depthAndBlobDataSynchronizerLock.lock();

                // Get the current final blobs and corresponding depth16 frame, synchronized together
                blobs = new ArrayList<>(mcvManager.published_final_blobs);
                fov_blobs = new ArrayList<>(mcvManager.published_forfov_blobs);
                System.arraycopy(DataBuffers.depth16FromToFSensorForRGB, 0, DataBuffers.depth16FromToFSensorPublished, 0, DataBuffers.depth16FromToFSensorForRGB.length);

                // RELEASE LOCK 2
                DataBuffers.depthAndBlobDataSynchronizerLock.unlock();

                // Handles generation of 3D anchors (blobs) if we are scanning
                // Also updates the screen coordinates for each blob
                handleBlobs(frame, camera, blobs, fov_blobs);

                // Log number of blobs after filtering
                mMetricsManager.record2dBlobsFiltered(blobs.size());

                //Record Normal Vector if required
                recordNormal(camera);
            }

            // If frame is ready, render camera preview image to the GL surface.
            drawRGBBackground(render);

            // RELEASE LOCK 1
            // All data written to DataBuffers, save file can now update if necessary
            DataBuffers.dataBufferPublisherLock.unlock();

            // Update entropy system with current position, and display if we have a reference anchor
            updateEntropySystem();

            // Check for ARCore tracking failures, if no tracking, don't do anything else
            if (checkForTrackingFailureAndDisplay(camera)) return;

            // Get our physical camera pose (not getting rotation or any virtual pose)
            final float[] cameraTranslation = camera.getPose().getTranslation();

            // Updates a number of text paramters on the textview
            updateDebugTextView(cameraTranslation);

            // Check if we are moving too fast, display UI message if so
            checkMovingTooFast(cameraTranslation);

            // Draw the tracked points
            drawPointCloud(render);

            // Draw any detected planes
            drawPlanes(render, camera);

            // Estimate lighting to draw anchors with color correction
            updateLightingForColorCorrection();

            // Visualize final camera anchors, which are offsets from the reference anchor
            visualizeCameraAnchors(render, colorCorrectionRgba);

            // Show anchors created by user bbox drawing
            visualizeBboxAnchors(render, colorCorrectionRgba);

            // Visualize current reference anchors
            // NOTE: THIS HAS TO BE BELOW THE CAMERA ANCHOR VIZ.
            // OTHERWISE, IT WILL OCCLUDE CAMERA ANCHORS.
            visualizeReferenceAnchors(render, colorCorrectionRgba);

            // Draw all the 2D stuff
            drawAllBoundingObjects();

            // Finally update status
            updateStatusTextView();


        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "!!! SERIOUS !!! Exception on the OpenGL thread", t);


            // Release any locks we hold
            if (DataBuffers.depthAndBlobDataSynchronizerLock.isHeldByCurrentThread()) {
                DataBuffers.depthAndBlobDataSynchronizerLock.unlock();
            }
            if (DataBuffers.depthAndBlobDataSynchronizerLock.isHeldByCurrentThread()) {
                DataBuffers.dataBufferPublisherLock.unlock();
            }
        }
    }

    private void updateStatusTextView() {
        final String finalText;
        switch (currentState) {
            case FINDING_REFERENCE_ANCHOR:
//                finalText = "Finding reference anchors...";
                finalText = getString(R.string.findingReferenceAnchor);
                break;
            case WAITING_FOR_USER_BBOX:
//                finalText = "Waiting for bbox to be drawn...";
                finalText = getString(R.string.waitingForBbox);
                break;
            case CALCULATING_IDEAL_DISTANCE:
//                finalText = "Waiting for depth scan completion...";
                finalText = getString(R.string.calculatingIdealDistance);
                break;
            case SCANNING_OBJECT:
//                finalText = "Scanning object...";
                finalText = getString(R.string.scanningObject);
                break;
            default:
                finalText = getString(R.string.unexpectedState);
        }
        this.runOnUiThread(() -> {
            statusTextView.setText(finalText);
        });
    }

    private void handleBboxSelection() {
        // User has to hit reset to remove old bbox - otherwise inadvertent taps could mess up scan
        if (currentState != CurrentState.WAITING_FOR_USER_BBOX) return;

        if (tapHelper.isInMotion) {
            queueDrawBoundingBoxAtScreenSpace(tapHelper.scrollStart.x, tapHelper.scrollStart.y,
                    tapHelper.scrollEnd.x, tapHelper.scrollEnd.y);
        } else if (tapHelper.newScrollEventReady) {
            // We want to create the anchor in the middle of the bbox (to be within the object itself)
            int midX = tapHelper.scrollStart.x + ((tapHelper.scrollEnd.x - tapHelper.scrollStart.x) / 2);
            int midY = tapHelper.scrollStart.y + ((tapHelper.scrollEnd.y - tapHelper.scrollStart.y) / 2);


            // Perform the transform from conf to RGB 2D coordinates
            float[] imageNormCoords = new float[2];
            frame.transformCoordinates2d(Coordinates2d.VIEW, new float[]{tapHelper.scrollStart.x, tapHelper.scrollStart.y}, Coordinates2d.IMAGE_NORMALIZED, imageNormCoords);
            float[] blobLocScrollStart = transformationUtil.imageNormalizedtoBlobLocation(imageNormCoords, tofCamera.getImageWidth(), tofCamera.getImageHeight());
            frame.transformCoordinates2d(Coordinates2d.VIEW, new float[]{tapHelper.scrollEnd.x, tapHelper.scrollEnd.y}, Coordinates2d.IMAGE_NORMALIZED, imageNormCoords);
            float[] blobLocScrollEnd = transformationUtil.imageNormalizedtoBlobLocation(imageNormCoords, tofCamera.getImageWidth(), tofCamera.getImageHeight());

            float distance_m = mcvManager.getDepthAtXYInMetresWithSmartROI(blobLocScrollStart[0], blobLocScrollStart[1],
                    blobLocScrollEnd[0], blobLocScrollEnd[1]);

            showToast("Distance: " + (Math.round(distance_m * 100.0) / 100.0) + " m");

            // Create midpoint anchor
            RelativeAnchor midpointAnchor = createRelativeAnchorFromHitTestWithEXACTDistance(midX, midY, distance_m);

            // Sometimes possible that anchor isn't created, show a toast indicating error
            if (midpointAnchor == null) {
                showToast("Could not create bbox anchors, please try again.");
                return;
            }

            // Hack the expected distance based on the size of the screen tap and the current distance to the object
            int tapXDist = Math.abs(tapHelper.scrollEnd.x - tapHelper.scrollStart.x);
            int tapYDist = Math.abs(tapHelper.scrollEnd.y - tapHelper.scrollStart.y);

            // The larger dist_m is, the more distance is represented for each tap
            // NOTE: MAGIC NUMBER FOR TAP <--> REAL WORLD X-Y RATIO AS PROPORTION OF DISTANCE
            float approxXDist = (float) tapYDist * distance_m * PhoneLidarConfig.TAP_X_DIST_TO_METERS_RATIO;
            float approxYDist = (float) tapXDist * distance_m * PhoneLidarConfig.TAP_X_DIST_TO_METERS_RATIO;

            float[] midPose = midpointAnchor.initialPos.clone();
            midPose[0] -= approxXDist / 2;
            midPose[1] -= approxYDist / 2;
            RelativeAnchor tlAnchor = new RelativeAnchor(midPose, midpointAnchor.color);

            midPose = midpointAnchor.initialPos.clone();
            midPose[0] += approxXDist / 2;
            midPose[1] += approxYDist / 2;
            RelativeAnchor brAnchor = new RelativeAnchor(midPose, midpointAnchor.color);

            // Create the overall bbox anchor based on user drag size
            ObjectBbox bbanchors = new ObjectBbox(midpointAnchor, tlAnchor, brAnchor);
            this.runOnUiThread(() -> {
                setBboxAnchorsAndStateAndVisibility(bbanchors);
            });

            // State transition since the user is now focusing on an exact object
            currentState = CurrentState.CALCULATING_IDEAL_DISTANCE;

            // Consume new scroll event
            tapHelper.newScrollEventReady = false;
        }

    }

    private RelativeAnchor createRelativeAnchorFromHitTest(float screenX, float screenY, float distance_m) {
        List<HitResult> hitResultList;
        hitResultList =
                frame.hitTestInstantPlacement(screenX, screenY, distance_m);

        for (HitResult hit : hitResultList) {
            Trackable trackable = hit.getTrackable();
            if (trackable instanceof InstantPlacementPoint) {
                return getRelativeAnchorFromHitAndTrackable(hit, trackable);
            }
        }
        return null;
    }


    /**
     * Method to force a precise distance for arcore hittest return anchor
     */
    private RelativeAnchor createRelativeAnchorFromHitTestWithEXACTDistance(float screenX, float screenY, float distance_m) {
        RelativeAnchor r = createRelativeAnchorFromHitTest(screenX, screenY, distance_m);
        if (r == null) return r;
        // We want the anchor to be exact distance_m away from the camera position and not the RANDOM place that arcore puts it

        // Convert the current camera pose to relative to save ops
        float[] relCurrentTrans = VectorUtils.subtract(currentCameraPose.getTranslation(), reference_anchor.anchor.getPose().getTranslation());

        // Get unit direction vector from the camera to anchor
        float[] dir = VectorUtils.normalize(VectorUtils.subtract(r.initialPos, relCurrentTrans));

        // Move ToF-distance along the line from camera pos --> anchor pos
        float[] finalPos = VectorUtils.add3d(relCurrentTrans, VectorUtils.scale(dir, distance_m));

        r.relativeAvgDrawPos = finalPos;
        r.initialPos = finalPos;
        return r;
    }

    Bitmap distScanBitmap;

    /**
     * If we are currently calculating the ideal distance to an object, run the depth scan procedure
     */
    private void runDepthScan(Camera camera) {
        // We need to have a valid bbox and currently trying to calculate the ideal dist to object
        if (currentObjectBboxAnchors == null || (currentState != CurrentState.CALCULATING_IDEAL_DISTANCE && currentState != CurrentState.SCANNING_OBJECT))
            return;

        // Get the screen coordinates of the midpoint anchor
//        double[] anchor2d = screenCoordsFromRelativePos(camera, bboxAnchors.midpointAnchor.relativeAvgDrawPos);

        // Calculate screen coordinates of the edges of the 3D bounding box
        double[] anchor2d_c1 = screenCoordsFromRelativePos(camera, currentObjectBboxAnchors.topLeftAnchor.relativeAvgDrawPos);
        double[] anchor2d_c2 = screenCoordsFromRelativePos(camera, currentObjectBboxAnchors.bottomRightAnchor.relativeAvgDrawPos);

        // For debugging, draw a circle around the anchors' screen coordinates
//        queueDrawBoundingCircleAtScreenSpace((float) anchor2d[0], (float) anchor2d[1], 40);
//        queueDrawBoundingCircleAtScreenSpace((float) anchor2d_c1[0], (float) anchor2d_c1[1], 40);
//        queueDrawBoundingCircleAtScreenSpace((float) anchor2d_c2[0], (float) anchor2d_c2[1], 40);

        // Transform anchor coordinates to depthconf space
        float[] imageNormCoords = new float[2];
        frame.transformCoordinates2d(Coordinates2d.VIEW, new float[]{(float) anchor2d_c1[0], (float) anchor2d_c1[1]}, Coordinates2d.IMAGE_NORMALIZED, imageNormCoords);
        float[] anchor2d_c1_t = transformationUtil.imageNormalizedtoBlobLocation(imageNormCoords, tofCamera.getImageWidth(), tofCamera.getImageHeight());
        frame.transformCoordinates2d(Coordinates2d.VIEW, new float[]{(float) anchor2d_c2[0], (float) anchor2d_c2[1]}, Coordinates2d.IMAGE_NORMALIZED, imageNormCoords);
        float[] anchor2d_c2_t = transformationUtil.imageNormalizedtoBlobLocation(imageNormCoords, tofCamera.getImageWidth(), tofCamera.getImageHeight());

        // Within the depthconf bbox, get the maximum number of connected components
        int maxCCSize = mcvManager.getMaxCCAtXYWithROI(anchor2d_c1_t[0],
                anchor2d_c1_t[1], anchor2d_c2_t[0], anchor2d_c2_t[1]);

        // Get the corresponding distance from the depth camera
        // However, we might want to get this from the 3D position
        float tof_distance_m = mcvManager.getDepthAtXYInMetresWithSmartROI(anchor2d_c1_t[0],
                anchor2d_c1_t[1], anchor2d_c2_t[0], anchor2d_c2_t[1]);
        this.latest_tof_dist = tof_distance_m;

        float z_distance_m = currentCameraPose.getTranslation()[2] - getAbsoluteAnchorPosition(currentObjectBboxAnchors.midpointAnchor.relativeAvgDrawPos, reference_anchor.anchor.getPose())[2];
        this.latest_z_dist = z_distance_m;


        // Don't update scan if we don't see the object
        if (tof_distance_m == 0) {
            this.runOnUiThread(() -> notVisibleWarningLayout.setVisibility(View.VISIBLE));
            // No more updates
            return;
        } else {
            this.runOnUiThread(() -> notVisibleWarningLayout.setVisibility(View.INVISIBLE));
        }

        // Don't add to depth scan if we can't see the object
        if (currentState == CurrentState.CALCULATING_IDEAL_DISTANCE) {

            currentObjectBboxAnchors.addTofDistanceCCObservation(z_distance_m, maxCCSize);

            // Display current state as a bitmap progres bar
//            distScanProgressBarTextureView.setVisibility(View.VISIBLE);
            Mat distanceScanMat = currentObjectBboxAnchors.distanceMapToMat(isShowAllScannedDistancesAsGreen);
            if (distScanBitmap == null) {
                distScanBitmap = Bitmap.createBitmap(distanceScanMat.cols(), distanceScanMat.rows(), Bitmap.Config.ARGB_4444);
            }

            // No ubyte in java, so 255 will be represented as -1
            byte[] distanceScanMatDist = new byte[3];
            distanceScanMat.get(0, 0, distanceScanMatDist);
            boolean farEnough = distanceScanMatDist[0] == -1 || distanceScanMatDist[1] == -1; // Either red or green == we have seen this dist
            distanceScanMat.get(0, distanceScanMat.cols() - 1, distanceScanMatDist);
            boolean closeEnough = distanceScanMatDist[0] == -1 || distanceScanMatDist[1] == -1;

            // Set the visibility and colors of the indicators at the end of the bars
            this.runOnUiThread(() -> {
                distScanCloseEnoughTextView.setVisibility(View.VISIBLE);
                distScanFarEnoughTextView.setVisibility(View.VISIBLE);
                int colorRed = ContextCompat.getColor(getApplicationContext(), R.color.red);
                int colorGreen = ContextCompat.getColor(getApplicationContext(), R.color.green);
                int colorBlack = ContextCompat.getColor(getApplicationContext(), R.color.black);
                int colorWhite = ContextCompat.getColor(getApplicationContext(), R.color.white);
                if (!closeEnough) {
                    distScanCloseEnoughTextView.setBackgroundColor(colorRed);
                    distScanCloseEnoughTextView.setTextColor(colorWhite);
                    distScanCloseEnoughTextView.setPaintFlags(distScanCloseEnoughTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    distScanCloseEnoughTextView.setBackgroundColor(colorGreen);
                    distScanCloseEnoughTextView.setTextColor(colorBlack);
                    distScanCloseEnoughTextView.setPaintFlags(distScanCloseEnoughTextView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                }

                if (!farEnough) {
                    distScanFarEnoughTextView.setBackgroundColor(colorRed);
                    distScanFarEnoughTextView.setTextColor(colorWhite);
                    distScanFarEnoughTextView.setPaintFlags(distScanFarEnoughTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    distScanFarEnoughTextView.setBackgroundColor(colorGreen);
                    distScanFarEnoughTextView.setTextColor(colorBlack);
                    distScanFarEnoughTextView.setPaintFlags(distScanFarEnoughTextView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                }
            });


            Utils.matToBitmap(distanceScanMat, distScanBitmap);
            renderDepthScanBitmapToTextureView(distScanBitmap, distScanProgressBarTextureView);

            if (currentObjectBboxAnchors.isDepthScanComplete()) {
                float idealDistance = currentObjectBboxAnchors.computeNaiveBestDistance();
                currentObjectBboxAnchors.setBestDistance(idealDistance);
                showToast("Ideal distance: " + idealDistance);
                currentState = CurrentState.SCANNING_OBJECT;
                initializeEntropyGrid(idealDistance);
            }

            // Set progress bar based on how large the CC is -- 25 is our max ML ROI size of 5x5
            if (maxCCSize > 25) {
                saturationProgressBar.setProgressTintList(ColorStateList.valueOf(Color.RED));
            } else {
                saturationProgressBar.setProgressTintList(ColorStateList.valueOf(Color.GREEN));
            }
            saturationProgressBar.setProgress(maxCCSize);
        } else {
//            distScanProgressBarTextureView.setVisibility(View.INVISIBLE);

            this.runOnUiThread(() -> {
                distScanCloseEnoughTextView.setVisibility(View.INVISIBLE);
                distScanFarEnoughTextView.setVisibility(View.INVISIBLE);
            });
        }
    }

    private void initializeEntropyGrid(float idealDistance) {
        // Compute size of the grid
        float additional_width = idealDistance * TAN10DEG;
        float additional_height = idealDistance * (TAN5DEG / 2.0f); // 2.5 degrees
        float half_width = Math.abs(currentObjectBboxAnchors.topLeftAnchor.relativeAvgDrawPos[0] - currentObjectBboxAnchors.bottomRightAnchor.relativeAvgDrawPos[0]) / 2 + additional_width;
        float half_height = Math.abs(currentObjectBboxAnchors.topLeftAnchor.relativeAvgDrawPos[1] - currentObjectBboxAnchors.bottomRightAnchor.relativeAvgDrawPos[1]) / 2 + additional_height;

        // Compute start position: just take bbox mid anchor and extend to idealDistance units away
        float[] startPosAbs = getAbsoluteAnchorPosition(currentObjectBboxAnchors.midpointAnchor.relativeAvgDrawPos, reference_anchor.anchor.getPose());
        // This moves the Z axis
        startPosAbs[2] += idealDistance;
        Pose initialPose = new Pose(startPosAbs, reference_anchor.anchor.getPose().getRotationQuaternion());

        // Start the entropy grid
        entropyGridSystem = new EntropyGridSystem(half_width, half_height, PhoneLidarConfig.MINIMAP_COL_DIST_METERS, PhoneLidarConfig.MINIMAP_ROW_DIST_METERS);
        entropyGridSystem.setInitialPose(initialPose);
        entropyGridSystem.setIsStarted(true);

        Log.d(TAG, String.format("Entropy hw: %f, hh: %f, idealDist: %f, relative bbox mid: %s, entropy grid midpoint: %s",
                half_width, half_height, idealDistance, Arrays.toString(currentObjectBboxAnchors.midpointAnchor.relativeAvgDrawPos), Arrays.toString(startPosAbs)));

        //            showToast("Entropy grid rows = " + entropyGridSystem.NUM_ROWS + " cols = " + entropyGridSystem.NUM_COLS);
    }

    private double[] screenCoordsFromRelativePos(Camera camera, float[] relativePos) {
        // Get projection matrix.
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

        // Get camera matrix and draw.
        camera.getViewMatrix(viewMatrix, 0);

        // Get the pose of the bbox midpoint
        Pose referencePose = reference_anchor.anchor.getPose();
        float[] anchorPosition = getAbsoluteAnchorPosition(relativePos, referencePose);
        Pose midAnchorPose = new Pose(anchorPosition, referencePose.getRotationQuaternion());
        midAnchorPose.toMatrix(modelMatrix, 0);
        return TransformationUtil.get_anchor_2d(modelMatrix, viewMatrix, projectionMatrix, 1, GLSURFACE_WIDTH, GLSURFACE_HEIGHT);
    }

    private float[] getAbsoluteAnchorPosition(float[] relativePos, Pose referencePose) {
        return VectorUtils.add3d(referencePose.getTranslation(), relativePos);
    }

    private void updatePointCloudState() {
        // Use try-with-resources to automatically release the point cloud.
        try (PointCloud pointCloud = frame.acquirePointCloud()) {
            FloatBuffer points = pointCloud.getPoints();
            this.latestNumPointCloudPoints = points.remaining() / 4; // Format of buffer is X Y Z confidence
            float totalConf = 0.0f;
            while (points.hasRemaining()) {
                int currentPos = points.position();
                float currentVal = points.get();
                if ((currentPos + 1) % 4 != 0)
                    continue; // Every 4th value is the confidence score
                // Add conf score if we're at the right point in the buffer
                totalConf += currentVal;
            }
            this.latestAvgPointCloudConf = totalConf / (float) this.latestNumPointCloudPoints;
        }
    }

    private void checkFPSTooLow(double averageFps) {
//        Log.d(TAG, "Average FPS: " + averageFps);
        if (averageFps < PhoneLidarConfig.MIN_TOF_FPS) {
            this.runOnUiThread(() -> {
                lowFPSWarningLayout.setVisibility(View.VISIBLE);
            });
        } else {
            this.runOnUiThread(() -> {
                lowFPSWarningLayout.setVisibility(View.INVISIBLE);
            });
        }
    }

    private void updateEntropySystem() {
        // If not supposed to update, keep everything hidden
        if (entropyGridSystem == null || !entropyGridSystem.getIsStarted()) {
            runOnUiThread(() -> {
                entropyGridView.setVisibility(View.INVISIBLE);
                scanProgressBar.setVisibility(View.INVISIBLE);

                rightArrowView.setVisibility(View.INVISIBLE);
                leftArrowView.setVisibility(View.INVISIBLE);
                upArrowView.setVisibility(View.INVISIBLE);
                downArrowView.setVisibility(View.INVISIBLE);
                displayOutOfScanAreaTextView(false);

            });
            return;
        }

        // Once in a few frames, update the entropy grid system with current pose
        if (frameCounter.get() % PhoneLidarConfig.FRAMES_UNTIL_RESAMPLE == 0) {
            entropyGridSystem.setCurrentPose(currentCameraPose);

            // We should only update the grid's entropy IF we are within the ideal distance AND the object is visible
            if (currentObjectBboxAnchors.getIsWithinIdealDistance() && currentObjectBboxAnchors.isVisible()) {
                entropyGridSystem.updateGridByPose();
            } else {
                entropyGridSystem.updateGridByPoseWithoutAddingPose();
            }

            // update the arrows
            List<Integer> directions = entropyGridSystem.getDirectionArrow();
            if ((directions.size() == 1 && directions.get(0) == 0) || entropyGridSystem.isMinimapComplete()) {
                runOnUiThread(() -> {
                    // within boundary OR we are done scanning, no arrows is needed to display
                    displayOutOfScanAreaTextView(false);
                    rightArrowView.setVisibility(View.INVISIBLE);
                    leftArrowView.setVisibility(View.INVISIBLE);
                    upArrowView.setVisibility(View.INVISIBLE);
                    downArrowView.setVisibility(View.INVISIBLE);
                });
            } else {
                runOnUiThread(() -> {
                    displayOutOfScanAreaTextView(true);
                    for (int direction : directions) {
                        switch (direction) {
                            case 1:
                                rightArrowView.setVisibility(View.VISIBLE);
                                break;
                            case 2:
                                leftArrowView.setVisibility(View.VISIBLE);
                                break;
                            case 3:
                                upArrowView.setVisibility(View.VISIBLE);
                                break;
                            case 4:
                                downArrowView.setVisibility(View.VISIBLE);
                                break;
                            default:
                                break;
                        }
                    }
                });
            }
        }

        // Run on every frame
        runOnUiThread(() -> {
            scanProgressBar.setVisibility(View.VISIBLE);
            entropyGridView.setVisibility(View.VISIBLE);
            int progress;

            // Show either full-grid progress or just progress for this square depending on setting
            if (isShowIndividualSquareProgressBar) {
                progress = entropyGridSystem.getCurrentPositionCompletionPercentage();
            } else {
                progress = entropyGridSystem.getCompletionPercentage();
            }
            scanProgressBar.setProgress(progress);

            // Make scan progress bar green when complete
            if (entropyGridSystem.isMinimapComplete()) {
                scanProgressBar.setProgressTintList(ColorStateList.valueOf(Color.GREEN));
                // We JUST completed the minimap, so update the historical bbox anchors list
                if (entropyGridSystem.isMinimapNewlyCompleteCallOnce()) {
                    currentObjectBboxAnchors.set2DScanComplete();
                    historical_complete_bbox_anchors.add(currentObjectBboxAnchors);
                    showToast("Scan complete!");
                }
            } else {
                scanProgressBar.setProgressTintList(ColorStateList.valueOf(Color.YELLOW));
            }

        });
        renderEntropyGrid();
    }

    private void updateBboxDistanceAfterDepthScan(Camera camera) {
        // We assume bboxAnchors are not null if we are in SCANNING mode.
        if (currentState != CurrentState.SCANNING_OBJECT) return;

        // Calculate screen coordinates of the edges of the 3D bounding box
        double[] anchor2d_c1 = screenCoordsFromRelativePos(camera, currentObjectBboxAnchors.topLeftAnchor.relativeAvgDrawPos);
        double[] anchor2d_c2 = screenCoordsFromRelativePos(camera, currentObjectBboxAnchors.bottomRightAnchor.relativeAvgDrawPos);

        // For debugging, draw a circle around the anchors' screen coordinates
//        queueDrawBoundingCircleAtScreenSpace((float) anchor2d[0], (float) anchor2d[1], 40);
//        queueDrawBoundingCircleAtScreenSpace((float) anchor2d_c1[0], (float) anchor2d_c1[1], 40);
//        queueDrawBoundingCircleAtScreenSpace((float) anchor2d_c2[0], (float) anchor2d_c2[1], 40);

        // Transform anchor coordinates to depthconf space
        float[] imageNormCoords = new float[2];
        frame.transformCoordinates2d(Coordinates2d.VIEW, new float[]{(float) anchor2d_c1[0], (float) anchor2d_c1[1]}, Coordinates2d.IMAGE_NORMALIZED, imageNormCoords);
        float[] anchor2d_c1_t = transformationUtil.imageNormalizedtoBlobLocation(imageNormCoords, tofCamera.getImageWidth(), tofCamera.getImageHeight());
        frame.transformCoordinates2d(Coordinates2d.VIEW, new float[]{(float) anchor2d_c2[0], (float) anchor2d_c2[1]}, Coordinates2d.IMAGE_NORMALIZED, imageNormCoords);
        float[] anchor2d_c2_t = transformationUtil.imageNormalizedtoBlobLocation(imageNormCoords, tofCamera.getImageWidth(), tofCamera.getImageHeight());

        // Get the corresponding distance from the depth camera
        // However, we might want to get this from the 3D position
        float tof_distance_m = mcvManager.getDepthAtXYInMetresWithSmartROI(anchor2d_c1_t[0],
                anchor2d_c1_t[1], anchor2d_c2_t[0], anchor2d_c2_t[1]);
        this.latest_tof_dist = tof_distance_m;

        // If the tof distance is 0, we can't see the object.
        if (tof_distance_m == 0) {
            currentObjectBboxAnchors.setNotVisible();
            notVisibleWarningLayout.setVisibility(View.VISIBLE);
        } else {
            currentObjectBboxAnchors.setAsVisible();
            notVisibleWarningLayout.setVisibility(View.INVISIBLE);
        }

        float z_distance_m = currentCameraPose.getTranslation()[2] - getAbsoluteAnchorPosition(currentObjectBboxAnchors.midpointAnchor.relativeAvgDrawPos, reference_anchor.anchor.getPose())[2];
        this.latest_z_dist = z_distance_m;

        // Update the bbox anchor object for whether we are within the ideal distance;
        currentObjectBboxAnchors.updateWithinIdealDistance(z_distance_m);

        // Create a mat for the ideal distance bar and where the user is in that bar
        Mat idealDistanceMat = currentObjectBboxAnchors.getIdealDistanceMat(z_distance_m);
        if (distScanBitmap == null) {
            distScanBitmap = Bitmap.createBitmap(idealDistanceMat.cols(), idealDistanceMat.rows(), Bitmap.Config.ARGB_4444);
        }
        Utils.matToBitmap(idealDistanceMat, distScanBitmap);
        renderDepthScanBitmapToTextureView(distScanBitmap, distScanProgressBarTextureView);

        //  We check for the currentState again since this could have changed by the time this thread executes
        this.runOnUiThread(() -> {
            // We display warnings if we are scanning and too close / too far
            // If the scan is complete, don't display any warnings
            if (currentState == CurrentState.SCANNING_OBJECT &&
                    !currentObjectBboxAnchors.getIsWithinIdealDistance() &&
                    !entropyGridSystem.isMinimapComplete()) {
                if (currentObjectBboxAnchors.getIsTooClose()) {
                    tooCloseTextView.setText(R.string.tooCloseText);
                } else {
                    tooCloseTextView.setText(R.string.tooFarText);
                }
                tooCloseWarningLayout.setVisibility(View.VISIBLE);
//                vibrator.vibrate(100);
            } else {
                tooCloseWarningLayout.setVisibility(View.INVISIBLE);
            }
        });
    }


    private void calculateTransformationMatrices() {
        if (frame.hasDisplayGeometryChanged() || calculateUVTransform) {
            // The UV Transform represents the transformation between screenspace in normalized units
            // and screenspace in units of pixels.  Having the size of each pixel is necessary in the
            // virtual object shader, to perform kernel-based blur effects.
            calculateUVTransform = false;
            float[] transform = getTextureTransformMatrix(frame);
            Log.i(TAG, "UVTransform: " + Arrays.toString(transform));
            virtualObjectDepthShader.setMatrix3("u_DepthUvTransform", transform);
        }

        if (frame.hasDisplayGeometryChanged() || calculateTransformationMatrix) {
            calculateTransformationMatrix = false;
            imageToViewTransformationMatrix = transformationUtil.getImageToViewMatrix(frame);
            Log.i(TAG, "ImageToScreenTransformationMatrix: " + Arrays.toString(imageToViewTransformationMatrix));
        }
    }


    private void renderEntropyGrid() {
        Mat entropyGridMat = entropyGridSystem.getEntropyGridMat();
        Bitmap entropyGridBitmap = Bitmap.createBitmap(entropyGridSystem.NUM_ROWS, entropyGridSystem.NUM_COLS, Bitmap.Config.ARGB_4444);
        Utils.matToBitmap(entropyGridMat, entropyGridBitmap);
        renderEntropyGridBitmapToTextureView(entropyGridBitmap, entropyGridView);
    }

    private void drawRGBBackground(SampleRender render) {
        if (isARCoreDisplayEnabled) {
            backgroundRenderer.draw(render, frame, depthSettings.depthColorVisualizationEnabled());
        }
    }

    private boolean checkForTrackingFailureAndDisplay(Camera camera) {
        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

        // If not tracking, don't draw 3D objects, show tracking failure reason instead.
        if (camera.getTrackingState() == TrackingState.PAUSED) {
            messageSnackbarHelper.showMessage(
                    this, TrackingStateHelper.getTrackingFailureReasonString(camera));
            return true;
        }
        return false;
    }

    private void updateDebugTextView(float[] cameraTranslation) {
        double tofFPS = this.mcvManager.averageFps;
        double glFPS = this.averageFps;
        String cameraPoseString = "X: " + cameraTranslation[0] + "\n" +
                "Y: " + cameraTranslation[1] + "\n" +
                "Z: " + cameraTranslation[2] + "\n" +
                "ToF FPS: " + Math.round(tofFPS * 100.0) / 100.0 + "\n" +
                "GL FPS: " + Math.round(glFPS * 100.0) / 100.0 + "\n" +
                "ToF Dist: " + Math.round(this.latest_tof_dist * 100.0) + "\n" +
                "Z Dist: " + Math.round(this.latest_z_dist * 100.0) + "\n" +
                "Points: " + this.latestNumPointCloudPoints + " < " + PhoneLidarConfig.MIN_NUM_POINTCLOUD_POINTS + "?\n" +
                "Points conf: " + (Math.round(this.latestAvgPointCloudConf * 100.0) / 100.0) + " < " + PhoneLidarConfig.MIN_POINTCLOUD_AVERAGE_CONFIDENCE + "?";
        this.runOnUiThread(() -> cameraPoseTextView.setText(cameraPoseString));
    }


    private void checkMovingTooFast(float[] cameraTranslation) {
        if (isMovingTooFastViewEnabled) {
            // Check if we are moving too fast (NOTE: this assumes we don't change cameraTranslation)
            movingTooFastHelper.updateTranslation(cameraTranslation);
            // If moving too fast, display message
            if (movingTooFastHelper.isMovingTooFast()) {
//                    Log.e("MOVING TOO FAST", "===MOVING TOO FAST===");
                this.runOnUiThread(() -> {
                    movingTooFastWarningLayout.setVisibility(View.VISIBLE);
                    vibrator.vibrate(100);
                    final Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> {
                        movingTooFastWarningLayout.setVisibility(View.INVISIBLE);
                        movingTooFastHelper.resetMovingTooFast();
                    }, 2000);
                });
            }
        }
    }


    private void drawPointCloud(SampleRender render) {
        if (isPointCloudVisualizationEnabled) {
            // Visualize tracked points.
            // Use try-with-resources to automatically release the point cloud.
            try (PointCloud pointCloud = frame.acquirePointCloud()) {
                if (pointCloud.getTimestamp() > lastPointCloudTimestamp) {
                    pointCloudVertexBuffer.set(pointCloud.getPoints());
                    lastPointCloudTimestamp = pointCloud.getTimestamp();
                }
                Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
                pointCloudShader.setMatrix4("u_ModelViewProjection", modelViewProjectionMatrix);
                render.draw(pointCloudMesh, pointCloudShader);
            }
        }
    }

    private void drawPlanes(SampleRender render, Camera camera) {
        // No tracking error at this point. If we detected any plane, then hide the
        // message UI, otherwise show searchingPlane message.
        //if (hasTrackingPlane()) {
        messageSnackbarHelper.hide(this);
        //} else {
        //    messageSnackbarHelper.showMessage(this, SEARCHING_PLANE_MESSAGE);
        //}

        // Visualize planes.
        if (isShowPlanesEnabled) {
            planeRenderer.drawPlanes(
                    render,
                    session.getAllTrackables(Plane.class),
                    camera.getDisplayOrientedPose(),
                    projectionMatrix);
        }
    }

    private void updateLightingForColorCorrection() {
        // Compute lighting from average intensity of the image.
        // The first three components are color scaling factors.
        // The last one is the average pixel intensity in gamma space.
        frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);
    }

    private void visualizeReferenceAnchors(SampleRender render, float[] colorCorrectionRgba) {
        if (reference_anchor == null) {
            return;
        }
//            float x_len = 2.0f * PhoneLidarConfig.VALID_REGION_HALF_WIDTH;
//            float y_len = 2.0f * PhoneLidarConfig.VALID_REGION_HALF_HEIGHT;
//            float z_len = 0.2f;

        float x_len = 0.03f;
        float y_len = 0.02f;
        float z_len = 0.01f;
        renderPoseToObject(reference_anchor.anchor.getPose(), render, referenceObjectMesh, referenceObjectShader, ColorArrays.RGB_ARRAY_BLUE, colorCorrectionRgba, x_len, y_len, z_len, 0.5f);

//        if (entropyGridSystem != null && entropyGridSystem.getIsStarted()) {
//            renderPoseToObject(entropyGridSystem.getInitialPose(), render, referenceObjectMesh, referenceObjectShader, RGB_ARRAY_BLUE, colorCorrectionRgba, 0.1f, 0.1f, 0.1f);
//        }
    }


    private void visualizeBboxAnchors(SampleRender render, float[] colorCorrectionRgba) {
        if (currentState != CurrentState.CALCULATING_IDEAL_DISTANCE
                && currentState != CurrentState.SCANNING_OBJECT
                && historical_complete_bbox_anchors.isEmpty())
            return;

        if (currentObjectBboxAnchors == null || currentObjectBboxAnchors.midpointAnchor == null || currentObjectBboxAnchors.topLeftAnchor == null || currentObjectBboxAnchors.bottomRightAnchor == null) {
            return;
        }

        // Display both historical and current bbox anchors
        List<ObjectBbox> anchorsToDisplay = new ArrayList<>(historical_complete_bbox_anchors);
        // We only add the existing anchor if we are scanning. Otherwise, it's added to the historical anchors list.
        // Otherwise, just draw the historical anchors
        if (!currentObjectBboxAnchors.get2DScanComplete()) {
            anchorsToDisplay.add(currentObjectBboxAnchors);
        }
        // Now draw all bbox anchors
        for (ObjectBbox bbAnchors : anchorsToDisplay) {
            if (bbAnchors == null) {
                continue;
            }

            // Get the pose from reference and add the relative position
            Pose referencePose = reference_anchor.anchor.getPose();
            float[] anchorPosition = VectorUtils.add3d(referencePose.getTranslation(), bbAnchors.midpointAnchor.relativeAvgDrawPos);
            Pose midAnchorPose = new Pose(anchorPosition, referencePose.getRotationQuaternion());
            // Transform Pose to matrix

            // Draw the midpoint hitTest point itself for reference
            float scale_factor = PhoneLidarConfig.BASE_SPHERE_SCALE / 2;
            renderPoseToObject(midAnchorPose, render, sphereObjectMesh, virtualObjectShader, ColorArrays.RGB_ARRAY_GREEN, colorCorrectionRgba, scale_factor, scale_factor, scale_factor, 0.3f);

            // Render top left pose
            anchorPosition = VectorUtils.add3d(referencePose.getTranslation(), bbAnchors.topLeftAnchor.relativeAvgDrawPos);
            Pose tlAnchorPose = new Pose(anchorPosition, referencePose.getRotationQuaternion());

            // Bottom right pose
            anchorPosition = VectorUtils.add3d(referencePose.getTranslation(), bbAnchors.bottomRightAnchor.relativeAvgDrawPos);
            Pose brAnchorPose = new Pose(anchorPosition, referencePose.getRotationQuaternion());

            float x_len_m = Math.abs(tlAnchorPose.getTranslation()[0] - brAnchorPose.getTranslation()[0]);
            float y_len_m = Math.abs(tlAnchorPose.getTranslation()[1] - brAnchorPose.getTranslation()[1]);
            float z_len_m = 0.10f;
            float[] color = bbAnchors.get2DScanComplete() ? ColorArrays.RGB_ARRAY_GREEN : ColorArrays.RGB_ARRAY_YELLOW;

            renderPoseToObject(midAnchorPose, render, referenceObjectMesh, referenceObjectShader, color, colorCorrectionRgba, x_len_m, y_len_m, z_len_m, 0.1f);

        }

        if (entropyGridSystem != null && entropyGridSystem.getIsStarted()) {
            // Draw the initial pose of the entropy grid system as a long plane
            Pose initialPose = entropyGridSystem.getInitialPose();
            float x_len_m = 50.0f;
            float y_len_m = 50.0f;
            float z_len_m = 0.01f;

            renderPoseToObject(initialPose, render, referenceObjectMesh, referenceObjectShader, ColorArrays.RGB_ARRAY_BLUE, colorCorrectionRgba, x_len_m, y_len_m, z_len_m, 0.1f);
        }


    }


    private void visualizeCameraAnchors(SampleRender render, float[] colorCorrectionRgba) {
        // Visualize anchors created
        if (entropyGridSystem == null || (!entropyGridSystem.isMinimapComplete() && isHideAnchorsWhileMinimapInProgress))
            return;

        for (Map.Entry<RelativeAnchor, Pair<Float, List<Pose>>> anchorEntry : anchor_heatmap.entrySet()) {
            RelativeAnchor relativeAnchor = anchorEntry.getKey();
            float anchorScore = anchorEntry.getValue().first;
            float[] relativePosition = relativeAnchor.relativeAvgDrawPos;
            // just need to check the x and y component is within the boundary
            boolean isInValidRegion = currentObjectBboxAnchors.withinValidRegionRelativePos(relativePosition, 0.05f);

            // If we're locked on, don't care about any other anchors
            if (isLockedOn && !relativeAnchor.lockedOn) continue;

            // Normal anchors (red/green based on entropy)
            if (anchorScore > PhoneLidarConfig.FOV_INFORMATION_THRESHOLD_SCORE) {
                Log.d(TAG, "^^ Anchor above info threshold");
                if (entropyGridSystem.isMinimapComplete() && isInValidRegion) {
                    // Don't show the anchor as green til we complete the scan
                    relativeAnchor.color = ColorArrays.RGB_ARRAY_GREEN;
                } else if (isColorHighEntropyBlobsBeforeMinimapCompleteEnabled) {
                    // For debugging, anchor is blue when enough entropy points within FOV.
                    relativeAnchor.color = ColorArrays.RGB_ARRAY_BLUE;
                } else {
                    // Otherwise, we just display it as orange
                    relativeAnchor.color = ColorArrays.RGB_ARRAY_ORANGE;
                }
            } else if (anchorScore > PhoneLidarConfig.ANCHOR_MIN_SCORE) {
                relativeAnchor.color = ColorArrays.RGB_ARRAY_ORANGE;
            } else {
                relativeAnchor.color = ColorArrays.RGB_ARRAY_RED;
            }

            // If dead, grey
            if (relativeAnchor.isDead) {
                relativeAnchor.color = ColorArrays.RGB_ARRAY_GRAY;
            }

            // Get the pose from reference and add the relative position
            Pose referencePose = reference_anchor.anchor.getPose();
            float[] anchorPosition = VectorUtils.add3d(referencePose.getTranslation(), relativeAnchor.relativeAvgDrawPos);
            Pose anchorPose = new Pose(anchorPosition, referencePose.getRotationQuaternion());
            // Transform Pose to matrix
            //Log.i(TAG, "modelMatrix: " + Arrays.toString(modelMatrix));


            boolean showAnchorOfThisSize = anchorScore > PhoneLidarConfig.ANCHOR_MIN_SCORE || isShowAnchorsBelowMinScore || relativeAnchor.isDead;
            if (showAnchorOfThisSize) {
                float scale_factor = PhoneLidarConfig.BASE_SPHERE_SCALE + (anchorScore * PhoneLidarConfig.PER_OCCURRENCE_SCALE_FACTOR);
                if (scale_factor > PhoneLidarConfig.MAX_SPHERE_SCALE) {
                    scale_factor = PhoneLidarConfig.MAX_SPHERE_SCALE;
                }
                renderPoseToObject(anchorPose, render, sphereObjectMesh, virtualObjectShader, relativeAnchor.color, colorCorrectionRgba, scale_factor, scale_factor, scale_factor, 0.3f);

                // Only draw all camera poses if we are asked to display all poses OR if we should only displaye locked poses and this one is locked
                if (!(isDisplayAllEntropyPoses || (isDisplayLockedEntropyPoses && relativeAnchor.lockedOn)))
                    continue;

                // DRAW ALL CAMERA POSES FOR THIS ANCHOR
                List<Pose> anchorPositions = anchorEntry.getValue().second;
                for (Pose p : anchorPositions) {
                    // Drawing each view pose directly
                    float[] viewPosition = p.getTranslation();
                    Pose viewLocationPose = new Pose(viewPosition, referencePose.getRotationQuaternion());
                    scale_factor = PhoneLidarConfig.BASE_SPHERE_SCALE / 3;
                    renderPoseToObject(viewLocationPose, render, sphereObjectMesh, virtualObjectShader, relativeAnchor.color, colorCorrectionRgba, scale_factor, scale_factor, scale_factor, 0.3f);

                    // Draw the line between the camera pose and the hidden camera location

                    /**
                     * We need to calculate the rotation direction between the camera and observation pos
                     * 1. Calculate Unit vector of (Pose1 translation - Pose2 translation)
                     * 2. Get the quaternion of rotation between the y-axis and the direction vector between pose1 and 2
                     */
                    // Get the vector representing the direction from viewPosition -> anchorPosition
                    float[] vector_between = VectorUtils.subtract(anchorPosition, viewPosition);
                    // Normalize to get just the direction
                    float[] unit_direction_vec = VectorUtils.normalize(vector_between);
                    // Find how much we need to rotate the up axis to rotate one vector to another
                    float[] rotationQuat = VectorUtils.getQuaternionBetween(LINE_ROTATION_AXIS, unit_direction_vec);
                    // The length of the line to draw is related to the distance between the points
                    float distance_between = VectorUtils.vecLength(vector_between);
                    float distance_scale_factor = 0.5f;

                    Pose cameraAnchorToHiddenCamPose = new Pose(viewPosition, rotationQuat);
                    renderPoseToObject(cameraAnchorToHiddenCamPose, render, virtualCylinderMesh, virtualObjectShader, ColorArrays.RGB_ARRAY_GREEN, colorCorrectionRgba, scale_factor * 0.5f, distance_between * distance_scale_factor, scale_factor * 0.5f, 0.3f);
                }
            }
        }
    }

    /**
     * Helper method to render a pose, using a particular renderer + mesh + shader
     *
     * @param anchorPose          Pose (translation and rotation) of object to draw
     * @param render              Renderer to use for final drawing
     * @param mesh                Point mesh defining the object shape
     * @param shader              Shader describing how to color the object
     * @param colorCorrectionRgba Transformation to color correct the object's shading
     * @param x_len_m             X (l -> r) size scaling for object
     * @param y_len_m             Y (d -> u) size scaling for object
     * @param z_len_m             Z (front -> back) size scaling for object
     */
    private void renderPoseToObject(Pose anchorPose, SampleRender render, Mesh mesh, Shader shader, float[] color, float[] colorCorrectionRgba, float x_len_m, float y_len_m, float z_len_m, float alpha) {
        anchorPose.toMatrix(modelMatrix, 0);
        Matrix.scaleM(modelMatrix, 0, x_len_m, y_len_m, z_len_m); // Scaling
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
        Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, LIGHT_DIRECTION, 0);

        shader
                .setMatrix4("u_ModelView", modelViewMatrix)
                .setMatrix4("u_ModelViewProjection", modelViewProjectionMatrix)
                .set4("u_ColorCorrection", colorCorrectionRgba)
                .set4("u_ViewLightDirection", viewLightDirection)
                .set3("u_AlbedoColor", color)
                .set1("u_alphaValue", alpha);
        render.draw(mesh, shader);
    }


    /**********************************************
     * END ToF-related callback and main draw
     **********************************************/


    /**********************************************
     * Render helpers
     **********************************************/

    private void renderDepthBitmapToTextureView(Bitmap bitmap, TextureView textureView) {
        Canvas canvas = textureView.lockCanvas();
        if (canvas == null) {
            Log.e(TAG, "Bitmap canvas is NULL, could not be locked!");
            return;
        }
        float heightRotatedScaleFactor = (float) textureView.getWidth() / (float) bitmap.getHeight();
        float widthRotatedScaleFactor = (float) textureView.getHeight() / (float) bitmap.getWidth();
        canvas.drawBitmap(bitmap, scaledBitmapTransform(textureView, heightRotatedScaleFactor, widthRotatedScaleFactor), null);
        textureView.unlockCanvasAndPost(canvas);
    }


    private void renderDepthScanBitmapToTextureView(Bitmap bitmap, TextureView textureView) {
        Canvas canvas = textureView.lockCanvas();
        if (canvas == null) {
            Log.e(TAG, "Bitmap canvas is NULL, could not be locked!");
            return;
        }
        canvas.drawBitmap(bitmap, null, new RectF(0, 0, (float) textureView.getWidth(), (float) textureView.getHeight()), null);
        textureView.unlockCanvasAndPost(canvas);
    }

    private void renderEntropyGridBitmapToTextureView(Bitmap bitmap, TextureView textureView) {
        Canvas canvas = textureView.lockCanvas();
        if (canvas == null) {
            Log.e(TAG, "Entropy canvas is NULL, could not be locked!");
            return;
        }
//        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawBitmap(bitmap, null, new RectF(0, 0, (float) textureView.getWidth(), (float) textureView.getHeight()), null);
        textureView.unlockCanvasAndPost(canvas);
    }

    private final int numFramesSeen = 0;

    private void displayTooCloseWarningIfNecessary(Mat depthOverlay) {
        if (isTooCloseVizualizationEnabled) {
            // int[] centerDepth = new int[1];
            int rect_size = 10;
            Rect roi = new Rect(depthOverlay.rows() / 2 - rect_size, depthOverlay.cols() / 2 - rect_size, rect_size, rect_size);
            Mat depthRoi = depthOverlay.submat(roi);
            int depthAvg = (int) (Core.sumElems(depthRoi).val[0] / (float) Core.countNonZero(depthRoi));
            // depthOverlay.get(depthOverlay.rows() / 2, depthOverlay.cols() / 2, centerDepth);
            // Log.e("CENTER DEPTH AVG", "" + depthAvg);
            if (depthAvg < PhoneLidarConfig.WARN_TOOCLOSE_DISTANCE_MM && depthAvg > 0) {
                tooCloseWarningLayout.setVisibility(View.VISIBLE);
                vibrator.vibrate(100);
            } else {
                tooCloseWarningLayout.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void displayOutOfScanAreaTextView(boolean shouldDisplay) {
        if (shouldDisplay) {
            outsideMapWarningLayout.setVisibility(View.VISIBLE);
            vibrator.vibrate(100);
        } else {
            outsideMapWarningLayout.setVisibility(View.INVISIBLE);
        }
    }

    private android.graphics.Matrix scaledBitmapTransform(TextureView view, float heightRotatedScaleFactor, float widthRotatedScaleFactor) {
        if (confidenceScaledBitmapTransform == null || view.getWidth() == 0 || view.getHeight() == 0) {
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.reset();
            matrix.postScale(widthRotatedScaleFactor, heightRotatedScaleFactor, 1.0f, 1.0f);
            matrix.postRotate(90, 0, 0);
            matrix.postTranslate(view.getWidth(), 0.0f);
            confidenceScaledBitmapTransform = matrix;
        }
        return confidenceScaledBitmapTransform;
    }

    private android.graphics.Matrix heatmapBitmapTransform(TextureView view, float heightRotatedScaleFactor, float widthRotatedScaleFactor) {
        if (heatmapBitmapTransform == null || view.getWidth() == 0 || view.getHeight() == 0) {
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.reset();
            matrix.postScale(widthRotatedScaleFactor, heightRotatedScaleFactor);//, 1.0f, 1.0f);
            matrix.postRotate(90, 0, 0);
            matrix.postTranslate(view.getWidth(), 0.0f);
            heatmapBitmapTransform = matrix;
        }
        return heatmapBitmapTransform;
    }


    /**********************************************
     * End render helpers
     **********************************************/


    // Anchors created from taps used for object placing with a given color.
    private static class ColoredAnchor {

        public final Anchor anchor;
        public float[] color;
        public final Trackable trackable;
        public boolean lockedOn = false;

        public ColoredAnchor(Anchor a, float[] color4f, Trackable trackable) {
            this.anchor = a;
            this.color = color4f;
            this.trackable = trackable;
        }
    }

    private static final HashMap<RelativeAnchor, Pair<Float, List<Pose>>> anchor_heatmap = new HashMap<>();
    private ColoredAnchor reference_anchor = null;
    private static final List<ObjectBbox> historical_complete_bbox_anchors = new ArrayList<>();
    private static ObjectBbox currentObjectBboxAnchors = null;

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] modelMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16]; // view x model
    private final float[] modelViewProjectionMatrix = new float[16]; // projection x view x model
    private final float[] viewLightDirection = new float[4]; // view x LIGHT_DIRECTION


    private void filterExistingAnchorHeatmap() {
        // Have to use iterator to avoid ConcurrentModificationException
        Iterator<Map.Entry<RelativeAnchor, Pair<Float, List<Pose>>>> it;

        if (frameCounter.get() > PhoneLidarConfig.ANCHOR_HEATMAP_REMOVE_TOOSMALL_ANCHORS_FREQUENCY_FRAMES) {
            // Either remove anchors that are too small every N frames, or only remove them once the minimap is complete.
            if (isRemovingSmallAnchors || (entropyGridSystem != null && entropyGridSystem.isMinimapComplete())) {
                // Periodically remove low frequency anchors which are likely to be false positives
                removeTooSmallAnchors();
                // reset frameCounter
                frameCounter.set(0);
            }
        }

        if (isDecayingAnchors) {
            // Decay the score of all anchors every frame
            it = anchor_heatmap.entrySet().iterator(); // re-assign the iterator
            while (it.hasNext()) {
                Map.Entry<RelativeAnchor, Pair<Float, List<Pose>>> item = it.next();
                Pair<Float, List<Pose>> value = item.getValue();
                float newScore = value.first - PhoneLidarConfig.ANCHOR_SCORE_DECAY_RATE_PER_FRAME;
                List<Pose> poses = value.second;
                item.setValue(new Pair<>(newScore, poses));
            }
        }
    }

    private void removeTooSmallAnchors() {
        Iterator<Map.Entry<RelativeAnchor, Pair<Float, List<Pose>>>> it = anchor_heatmap.entrySet().iterator();
        int num_anchors_removed = 0;
        while (it.hasNext()) {
            Map.Entry<RelativeAnchor, Pair<Float, List<Pose>>> item = it.next();
//                    Log.i(TAG, "heatmap value: " + item.getValue());
            // If the system isn't locked on OR we're locked on to something and the current item isn't it
            // AND we're below the min occcurrence
            // AND we are not dead
            if (item.getValue().first < PhoneLidarConfig.ANCHOR_MIN_SCORE &&
                    !item.getKey().isDead) {
                it.remove();
                num_anchors_removed++;
            }
        }

        // record metrics
        mMetricsManager.recordAnchorsRemoved(num_anchors_removed);
    }

    private void logMetricsPeriodically() {
        // Report metrics every second
        if (PhoneLidarConfig.SHOULD_REPORT_METRICS && frameCounter.get() % 30 == 0) {
            // record heatmap
            mMetricsManager.recordHeatmap(anchor_heatmap);
            // format metrics
            String metricsString = mMetricsManager.formatMetrics();
            // log
            Log.v(TAG, metricsString);


            // Log bbox anchor distance info
            if (currentObjectBboxAnchors != null) {
                Log.v(TAG, currentObjectBboxAnchors.getObjectDistanceString());
            }
        }


        // Clear metrics after reporting
        mMetricsManager.clearMetrics();
    }


    // Return true if the anchor is new
    // Return false if the anchor is close to any of the existing anchors
    private boolean updateAnchorHeatmap(RelativeAnchor anchor, Pose currentCameraPose, boolean addScoreIfInformationGained, boolean addAnchorIfNew) {
        // Get reference anchor position
        float[] referencePosition = reference_anchor.anchor.getPose().getTranslation();
        // Get anchor position
        float[] anchorPosition = VectorUtils.add3d(anchor.relativeAvgDrawPos, referencePosition);
        float[] currentPosePosition = currentCameraPose.getTranslation();
        //Log.i(TAG, "anchorTranslation: " + Arrays.toString(anchorPosition));

        // Then we check the euclidean distance from the new anchor to all existing anchors
        for (RelativeAnchor existingAnchor : anchor_heatmap.keySet()) {
            float[] existingAnchorPosition = VectorUtils.add3d(existingAnchor.initialPos, referencePosition);
            double distance = VectorUtils.vecDistance(anchorPosition, existingAnchorPosition);// 0.1f);

            // if anchor exists and is "part" of another existing anchor
            if (distance <= PhoneLidarConfig.ANCHOR_STICKY_RADIUS) {
                Pair<Float, List<Pose>> value = anchor_heatmap.get(existingAnchor);
                if (isFOVFiltering) {
                    // NEW FOV FILTERING ALGORITHM
                    // check with all old poses
                    List<Pose> oldPoses = value.second;
                    // find the horizontal distance to all old poses
                    // if within an epsilon distance, we consider no information gain
                    // else we add score by 1
                    boolean informationGained = true;
                    boolean outsideFOV = false;
                    for (Pose oldPose : oldPoses) {
                        float[] posePosition = oldPose.getTranslation();
                        float horizontalDistance = VectorUtils.calculateProjectedDistanceOnPlane(currentPosePosition, posePosition);
                        // check with epsilon distance
                        if (horizontalDistance < PhoneLidarConfig.FOV_EPSILON_DISTANCE_FOR_NEW_POSE) {
                            informationGained = false;
                            break;
                        }
                        // check with max allowed distance
                        if (horizontalDistance > PhoneLidarConfig.MAX_ALLOWED_DISTANCE_BEFORE_TOOLARGE_FOV) {
                            // we should decrease score
                            outsideFOV = true;
                            break;
                        }
                    }

                    // we only add score when there's information gained
                    // this prevents adding score to stationary position
                    if (informationGained) {
                        float newScore = value.first;
                        if (outsideFOV) {
                            // set score as 0, which is essentially marking as dead
                            // newScore = 0.0f;
                            existingAnchor.numStrikes++;
                            if (existingAnchor.numStrikes > PhoneLidarConfig.MAX_NUM_OUT_OF_FOV_STRIKES) {
                                existingAnchor.isDead = true;
                                newScore = 1.0f;
                            }
                        } else {
                            // increment score by 1
                            if (addScoreIfInformationGained) {
                                newScore += PhoneLidarConfig.FOV_INFORMATION_GAINED_SCORE_INCREMENT;
                            }
                        }
                        // add new camera pose if we gained any info with this pose
                        List<Pose> newPoses = value.second;
                        newPoses.add(currentCameraPose);
                        // put new value
                        Pair<Float, List<Pose>> newValue = new Pair<>(newScore, newPoses);
                        anchor_heatmap.put(existingAnchor, newValue);
                    }

                    if (isUpdateAnchorPositionOverTime) {
                        // Continue to average the location EVEN if we have seen this point before-ish
                        // Solidify location across time
                        existingAnchor.computeNewRelativePos(anchor.relativeAvgDrawPos);
                    }

                } else {
                    if (addScoreIfInformationGained) {
                        // OLD JUST-ADD-1-IF-OVERLAP ALGORITHM
                        // increment score by 1
                        float newScore = value.first + 1.0f;
                        Pair<Float, List<Pose>> newValue = new Pair<>(newScore, value.second);
                        // put new value
                        anchor_heatmap.put(existingAnchor, value);
                    }
                }
                return false;
            }
        }
        // not belong to any of the existing anchor
        // add the anchor to heatmap
        if (addAnchorIfNew) {
            List<Pose> anchorPoses = new ArrayList<>();
            anchorPoses.add(currentCameraPose);
            Pair<Float, List<Pose>> anchorValue = new Pair<>(1.0f, anchorPoses);
            anchor_heatmap.put(anchor, anchorValue);
        }

        return true;
    }

    /****************************
     * 2D DRAWING CODE
     ****************************/


    private final List<Pair<android.graphics.PointF, Float>> boundingCirclesRed = new ArrayList<>();
    private final List<Pair<android.graphics.PointF, Float>> boundingCirclesGreen = new ArrayList<>();
    private final List<Pair<android.graphics.PointF, android.graphics.PointF>> boundingBoxes = new ArrayList<>();

    private void queueDrawBoundingCircleAtScreenSpace(float centerX, float centerY, float radius) {
        boundingCirclesRed.add(new Pair<>(new android.graphics.PointF(centerX, centerY), radius));
    }

    private void queueDrawGreenBoundingCircleAtScreenSpace(float centerX, float centerY, float radius) {
        boundingCirclesGreen.add(new Pair<>(new android.graphics.PointF(centerX, centerY), radius));
    }

    private void queueDrawBoundingBoxAtScreenSpace(float left, float top, float right, float bottom) {
        boundingBoxes.add(new Pair<>(new android.graphics.PointF(left, top), new android.graphics.PointF(right, bottom)));
    }

    private void drawAllBoundingObjects() {
        Canvas bboxOverlayCanvas = bboxOverlay.lockCanvas();
        if (bboxOverlayCanvas == null) {
            Log.e(TAG, "Bbox overlay canvas is NULL, could not be locked!");
            return;
        }
        bboxOverlayCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        for (Pair<android.graphics.PointF, Float> p : boundingCirclesRed) {
            bboxOverlayCanvas.drawCircle(p.first.x, p.first.y, p.second, bboxPainter);
        }
        for (Pair<android.graphics.PointF, Float> p : boundingCirclesGreen) {
            bboxOverlayCanvas.drawCircle(p.first.x, p.first.y, p.second, bboxPainterGreen);
        }
        for (Pair<android.graphics.PointF, android.graphics.PointF> p : boundingBoxes) {
            bboxOverlayCanvas.drawRect(p.first.x, p.first.y, p.second.x, p.second.y, bboxPainter);
        }
        bboxOverlay.unlockCanvasAndPost(bboxOverlayCanvas);
        boundingCirclesRed.clear();
        boundingCirclesGreen.clear();
        boundingBoxes.clear();
    }


    // Handle blobs per frame
    private void handleBlobs(Frame frame, Camera camera, List<Keypoint> blobs, List<Keypoint> fovFilterCancelBlobs) {
        if (blobs.isEmpty() && fovFilterCancelBlobs.isEmpty()) return;

        // record number of anchors created per frame
        int num_anchors_created = 0;
        int num_anchors_added_to_existing = 0;
        int total_num_anchors = anchor_heatmap.keySet().size();

        for (int i = 0; i < blobs.size(); i++) {
            Keypoint blob = blobs.get(i);

            // Perform the transform from conf to RGB 2D coordinates
            float[] screenCoords = new float[2];
            float[] locationNorm = transformationUtil.blobLocationToImageNormalized(blob, tofCamera.getImageWidth(), tofCamera.getImageHeight());
            float radius = getBlobScreenRadius();
            frame.transformCoordinates2d(Coordinates2d.IMAGE_NORMALIZED, locationNorm, Coordinates2d.VIEW, screenCoords);

            // Draw the location of the blob on the screen
            queueDrawBoundingCircleAtScreenSpace(screenCoords[0], screenCoords[1], radius);

            // Update the final screen coordinates so that we can use it offline later with RGB data
            blobs.get(i).screenCoords_x = screenCoords[0];
            blobs.get(i).screenCoords_y = screenCoords[1];

            // We should have some stable anchors in anchor heatmap, we use instant placement to find the relative position of the anchor
            // Only update heatmap if we are scanning, within ideal distance, and we haven't completed a minimap scan yet
            if (currentState == CurrentState.SCANNING_OBJECT && currentObjectBboxAnchors.getIsWithinIdealDistance() &&
                    !entropyGridSystem.isMinimapComplete()) {
                double distance_m = blob.depthMetres;
                RelativeAnchor relativeAnchor = createRelativeAnchorFromHitTestWithEXACTDistance(screenCoords[0], screenCoords[1], (float) distance_m);
                boolean addNewAnchor = updateAnchorHeatmap(relativeAnchor, camera.getPose(), true, true);
                if (addNewAnchor) {
                    num_anchors_created++;
                } else {
                    num_anchors_added_to_existing++;
                }
            }
        }

        // Only bother to compute FOV filter blobs etc if we are really scanning, otherwise, save cycles
        if (currentState != CurrentState.SCANNING_OBJECT || !currentObjectBboxAnchors.getIsWithinIdealDistance())
            return;

        // Add to metrics
        mMetricsManager.recordAnchorsPerFrame(num_anchors_created, num_anchors_added_to_existing, total_num_anchors);

        for (int i = 0; i < fovFilterCancelBlobs.size(); i++) {
            Keypoint blob = fovFilterCancelBlobs.get(i);

            // Perform the transform from conf to RGB 2D coordinates
            float[] screenCoords = new float[2];
            float[] locationNorm = transformationUtil.blobLocationToImageNormalized(blob, tofCamera.getImageWidth(), tofCamera.getImageHeight());
            frame.transformCoordinates2d(Coordinates2d.IMAGE_NORMALIZED, locationNorm, Coordinates2d.VIEW, screenCoords);
            // Draw the location of the blob on the screen
            if (isShowAllFovFilterBlobs) {
                float radius = getBlobScreenRadius();
                queueDrawGreenBoundingCircleAtScreenSpace(screenCoords[0], screenCoords[1], radius);
            }

            // Update the final screen coordinates so that we can use it offline later with RGB data
            fovFilterCancelBlobs.get(i).screenCoords_x = screenCoords[0];
            fovFilterCancelBlobs.get(i).screenCoords_y = screenCoords[1];

            // We should have some stable anchors in anchor heatmap, we use instant placement to find the relative position of the anchor
            double distance_m = blob.depthMetres;
            RelativeAnchor relativeAnchor = createRelativeAnchorFromHitTestWithEXACTDistance(screenCoords[0], screenCoords[1], (float) distance_m);
            updateAnchorHeatmap(relativeAnchor, camera.getPose(), false, false);
        }
    }

    private float getBlobScreenRadius() {
        float ratio = (float) GLSURFACE_WIDTH / (float) tofCamera.getImageHeight();
        return ratio * BOUNDING_BOX_DISPLAY_SCALE;
    }

    private RelativeAnchor getRelativeAnchorFromHitAndTrackable(HitResult hit, Trackable trackable) {
        float[] objColor = getTrackableColor(trackable);
        Anchor hitAnchor = hit.createAnchor();
        float[] hitAnchorPos = hitAnchor.getPose().getTranslation();
        hitAnchor.detach();
        // get position
        float[] referencePos = reference_anchor.anchor.getPose().getTranslation();
        float[] relativePos = VectorUtils.subtract(hitAnchorPos, referencePos);
        return new RelativeAnchor(relativePos, objColor);
    }

    // return true if new anchor created, false otherwise
    private boolean createReferenceAnchors(Frame frame, Session session) {
        if (isWaitToCreateReferenceAnchors) {
            // Only create reference anchor if we have enough good-quality pointcloud points
            // Aims to avoid reference anchor jumping
            // Use try-with-resources to automatically release the point cloud.
            int numPoints = this.latestNumPointCloudPoints;
            float avgConf = this.latestAvgPointCloudConf;
            Log.d(TAG, "Waiting to place reference point, Pointcloud points: " + numPoints + "\tAvg Conf: " + avgConf);
            if (numPoints < PhoneLidarConfig.MIN_NUM_POINTCLOUD_POINTS ||
                    avgConf < PhoneLidarConfig.MIN_POINTCLOUD_AVERAGE_CONFIDENCE) {
                return false;
            }
        }

        // Do hit test at the center of screen
        // to create some reference anchors
        List<HitResult> hitResultList;
        hitResultList = frame.hitTest((float) GLSURFACE_WIDTH / 2.0f, (float) GLSURFACE_HEIGHT / 2.0f);
        for (HitResult hit : hitResultList) {
            Trackable trackable = hit.getTrackable();
            // Only take point which should be stable
            if (trackable instanceof Point) {
                float[] anchorColor = ColorArrays.RGB_ARRAY_YELLOW;
                Anchor hitAnchor = hit.createAnchor();
                float[] zeroRotation = new float[]{0, 0, 0, 1};
                float[] anchorPosition = hitAnchor.getPose().getTranslation();
                Anchor updatedAnchor = session.createAnchor(new Pose(anchorPosition, zeroRotation));
                ColoredAnchor newAnchor = new ColoredAnchor(updatedAnchor, anchorColor, trackable);
                setReferenceAnchorAndState(newAnchor);
                Log.i(TAG, "reference anchor created");
                return true;
            }
        }
        return false;
    }

    private void setReferenceAnchorAndState(ColoredAnchor newAnchor) {
        reference_anchor = newAnchor;
        currentState = CurrentState.WAITING_FOR_USER_BBOX;
        // It's really common that the user touches the screen a lot before reference anchor is put down, fix that
        // Otherwise, we transition immediately to depth scan, confusingly
        tapHelper.resetScrollData();
    }

    private void clearReferenceAnchorAndState() {
        reference_anchor = null;
        currentState = CurrentState.FINDING_REFERENCE_ANCHOR;
    }

    private void clearBboxAnchorsAndStateAndVisibility() {
        // Setting this to null makes certain threading code crash. Just change state.
        // bboxAnchors = null;
        if (currentState == CurrentState.CALCULATING_IDEAL_DISTANCE || currentState == CurrentState.SCANNING_OBJECT) {
            currentState = CurrentState.WAITING_FOR_USER_BBOX;
            distScanProgressBarLayout.setVisibility(View.INVISIBLE);
        }
        notVisibleWarningLayout.setVisibility(View.INVISIBLE);
        tapHelper.resetScrollData();
    }


    private void setBboxAnchorsAndStateAndVisibility(ObjectBbox bboxAnchor) {
        currentObjectBboxAnchors = bboxAnchor;
        currentState = CurrentState.CALCULATING_IDEAL_DISTANCE;
        distScanProgressBarLayout.setVisibility(View.VISIBLE);
    }


    // Locked-on state to display all seen-from vectors of a given anchor
    private boolean isLockedOn = false;

    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
            Log.d(TAG, "TAP X: " + tap.getX() + " / " + GLSURFACE_WIDTH + ", Y: " + tap.getY() + " / " + GLSURFACE_HEIGHT);

            if (isLockedOn) {
                // Unlock any anchors that might be locked
                for (RelativeAnchor r : anchor_heatmap.keySet()) {
                    r.lockedOn = false;
                }
                isLockedOn = false;
                Log.w(TAG, "Tap: Lock released from anchor!");
                return;
            }

            if (!isLockedOn) {
                // Find the closest point to the tap and lock onto that anchor
                double minDist = Double.MAX_VALUE;
                RelativeAnchor closestAnchor = null;
                double[] tapCoords = new double[]{tap.getX(), tap.getY()};

                for (RelativeAnchor r : anchor_heatmap.keySet()) {
                    double[] r_screenCoords = screenCoordsFromRelativePos(camera, r.relativeAvgDrawPos);
                    double dist = VectorUtils.vecDistanceWithoutZ(tapCoords, r_screenCoords);
                    if (dist < minDist) {
                        minDist = dist;
                        closestAnchor = r;
                    }
                }
                if (closestAnchor != null) {
                    // We lock on to the closest anchor
                    closestAnchor.lockedOn = true;
                    isLockedOn = true;
                    Log.w(TAG, "Tap: Locked onto an anchor! " + Arrays.toString(closestAnchor.relativeAvgDrawPos));
                } else {
                    Log.w(TAG, "Tap: Could not find any closest anchors, should only happen for empty heatmap");
                }
            }
        }
    }


    /**
     * Updates current and averaged FPS. Should be called for every new frame.
     */
    private void updateFps() {
        this.fpsFrameCounter++;
        long currentTimeMillis = System.currentTimeMillis();

        // Average FPS calculations
        if (this.fpsFrameCounter == 1) {
            // First frame in the set, set up last frame time
            this.firstFrameTimeMillis = currentTimeMillis;
        } else if (this.fpsFrameCounter >= this.FPS_LOOKBACK_FRAMES) {
            // Done with seeing all frames, get average FPS
            double secondsElapsed = (currentTimeMillis - this.firstFrameTimeMillis) / 1000.0f;
            double averageFps = this.fpsFrameCounter / secondsElapsed;
            this.averageFps = averageFps;
            this.fpsFrameCounter = 0;
        }

        // Instant FPS calculation
        double instantFps = 1000.0 / (currentTimeMillis - lastFrameTime);
        this.lastFrameTime = currentTimeMillis;

    }


    /****************************
     * SETTINGS MENUS
     ****************************/


    /**
     * Menu button to launch feature specific settings.
     */
    protected boolean settingsMenuClick(MenuItem item) {
        if (item.getItemId() == R.id.depth_settings) {
            launchDepthSettingsMenuDialog();
            return true;
        } else if (item.getItemId() == R.id.instant_placement_settings) {
            launchInstantPlacementSettingsMenuDialog();
            return true;
        } else if (item.getItemId() == R.id.auto_focus_settings) {
            launchAutoFocusSettingsMenuDialog();
            return true;
        } else if (item.getItemId() == R.id.filter_settings) {
            launchFilterSettingsMenuDialog();
            return true;
        } else if (item.getItemId() == R.id.filter_3d_settings) {
            launchFilter3DSettingsMenuDialog();
            return true;
        } else if (item.getItemId() == R.id.ui_settings) {
            launchUiSettingsMenuDialog();
        } else if (item.getItemId() == R.id.filter_values_settings) {
            launchFilterValuesMenuDialog();
        }
        return false;
    }

    private void launchInstantPlacementSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        Resources resources = getResources();
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_instant_placement)
                .setMultiChoiceItems(
                        resources.getStringArray(R.array.instant_placement_options_array),
                        instantPlacementSettingsMenuDialogCheckboxes,
                        (DialogInterface dialog, int which, boolean isChecked) ->
                                instantPlacementSettingsMenuDialogCheckboxes[which] = isChecked)
                .setPositiveButton(
                        R.string.done,
                        (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                .setNegativeButton(
                        android.R.string.cancel,
                        (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                .show();
    }


    /**
     * Shows checkboxes to the user to facilitate toggling of depth-based effects.
     */
    private void launchDepthSettingsMenuDialog() {
        // Retrieves the current settings to show in the checkboxes.
        resetSettingsMenuDialogCheckboxes();

        // Shows the dialog to the user.
        Resources resources = getResources();
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            // With depth support, the user can select visualization options.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_with_depth)
                    .setMultiChoiceItems(
                            resources.getStringArray(R.array.depth_options_array),
                            depthSettingsMenuDialogCheckboxes,
                            (DialogInterface dialog, int which, boolean isChecked) ->
                                    depthSettingsMenuDialogCheckboxes[which] = isChecked)
                    .setPositiveButton(
                            R.string.done,
                            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                    .setNegativeButton(
                            android.R.string.cancel,
                            (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                    .show();
        } else {
            // Without depth support, no settings are available.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_without_depth)
                    .setPositiveButton(
                            R.string.done,
                            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                    .show();
        }
    }

    /**
     * Sets whether we force autofocus on for the RGB cam (to allow for better snapshots) or leave the default "best ARCore" setting
     */
    private void launchAutoFocusSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        Resources resources = getResources();
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_auto_focus)
                .setMultiChoiceItems(
                        resources.getStringArray(R.array.auto_focus_options_array),
                        autoFocusSettingsMenuDialogCheckboxes,
                        (DialogInterface dialog, int which, boolean isChecked) ->
                                autoFocusSettingsMenuDialogCheckboxes[which] = isChecked)
                .setPositiveButton(
                        R.string.done,
                        (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                .setNegativeButton(
                        android.R.string.cancel,
                        (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                .show();
    }


    private void launchFilterSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        Resources resources = getResources();
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_filters)
                .setMultiChoiceItems(
                        resources.getStringArray(R.array.filters_options_array),
                        filterSettingsMenuDialogCheckboxes,
                        (DialogInterface dialog, int which, boolean isChecked) ->
                                filterSettingsMenuDialogCheckboxes[which] = isChecked)
                .setPositiveButton(
                        R.string.done,
                        (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                .setNegativeButton(
                        android.R.string.cancel,
                        (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                .show();
    }


    private void launchFilter3DSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        Resources resources = getResources();
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_filters_3d)
                .setMultiChoiceItems(
                        resources.getStringArray(R.array.filters_3d_options_array),
                        filter3DSettings3MenuDialogCheckboxes,
                        (DialogInterface dialog, int which, boolean isChecked) ->
                                filter3DSettings3MenuDialogCheckboxes[which] = isChecked)
                .setPositiveButton(
                        R.string.done,
                        (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                .setNegativeButton(
                        android.R.string.cancel,
                        (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                .show();
    }


    private void launchUiSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        Resources resources = getResources();
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_ui)
                .setMultiChoiceItems(
                        resources.getStringArray(R.array.ui_options_array),
                        uiSettingsMenuDialogCheckboxes,
                        (DialogInterface dialog, int which, boolean isChecked) ->
                                uiSettingsMenuDialogCheckboxes[which] = isChecked)
                .setPositiveButton(
                        R.string.done,
                        (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                .setNegativeButton(
                        android.R.string.cancel,
                        (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                .show();
    }


    private void launchFilterValuesMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        Resources resources = getResources();
        View dialogLayout = LayoutInflater.from(this).inflate(R.layout.filters_values, null);

        EditText mlThresh = dialogLayout.findViewById(R.id.mlThreshEdit);
        EditText compactness = dialogLayout.findViewById(R.id.compactnessEdit);
        EditText maxCC = dialogLayout.findViewById(R.id.maxCCEdit);
        EditText maxBboxDiff = dialogLayout.findViewById(R.id.maxBboxDiffEdit);
        EditText minDepth = dialogLayout.findViewById(R.id.minDepthEdit);
        EditText maxDepth = dialogLayout.findViewById(R.id.maxDepthEdit);

        Spinner mlModelSpinner = dialogLayout.findViewById(R.id.mlModelSpinner);
        ArrayAdapter<String> mlModelItems = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_item, FilterModel.MODEL_NAMES);
        mlModelItems.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mlModelSpinner.setAdapter(mlModelItems);
        mlModelSpinner.setSelection(FilterModel.currentModelIndex);

        mlThresh.setText(String.valueOf(PhoneLidarConfig.MIN_CAMERA_PREDICTION_PROBABILITY));
        compactness.setText(String.valueOf(PhoneLidarConfig.MIN_COMPACTNESS));
        maxCC.setText(String.valueOf(PhoneLidarConfig.MAX_SIZE_CONNECTED_COMPONENT));
        maxBboxDiff.setText(String.valueOf(PhoneLidarConfig.MAX_BBOX_WIDTH_HEIGHT_DIFFERENCE_PX));
        minDepth.setText(String.valueOf(PhoneLidarConfig.KNOWN_RANGE_MIN / 10));
        maxDepth.setText(String.valueOf(PhoneLidarConfig.KNOWN_RANGE_MAX / 10));

        new AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setTitle("Filter Values")
                .setPositiveButton("OK", (dialog, id) -> {
                    final Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> {
                        PhoneLidarConfig.MIN_CAMERA_PREDICTION_PROBABILITY = Float.parseFloat(mlThresh.getText().toString());
                        PhoneLidarConfig.MIN_COMPACTNESS = Float.parseFloat(compactness.getText().toString());
                        PhoneLidarConfig.MAX_SIZE_CONNECTED_COMPONENT = Integer.parseInt(maxCC.getText().toString());
                        PhoneLidarConfig.MAX_BBOX_WIDTH_HEIGHT_DIFFERENCE_PX = Integer.parseInt(maxBboxDiff.getText().toString());
                        PhoneLidarConfig.setKnownRangeMinCm(Integer.parseInt(minDepth.getText().toString()));
                        PhoneLidarConfig.setKnownRangeMaxCm(Integer.parseInt(maxDepth.getText().toString()));
                        FilterModel.setModelIndex(mlModelSpinner.getSelectedItemPosition());
                        updateMLSeekBarValueFromSettings();
                    }, PhoneLidarConfig.SETTINGS_CHANGE_DELAY_MILLIS);

                })
                .setNegativeButton(
                        android.R.string.cancel,
                        (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                .show();
    }


    private void applySettingsMenuDialogCheckboxes() {
        depthSettings.setUseDepthForOcclusion(depthSettingsMenuDialogCheckboxes[0]);
        depthSettings.setDepthColorVisualizationEnabled(depthSettingsMenuDialogCheckboxes[1]);
        instantPlacementSettings.setInstantPlacementEnabled(
                instantPlacementSettingsMenuDialogCheckboxes[0]);

        isAutoFocusEnabled = autoFocusSettingsMenuDialogCheckboxes[0];

        isDepthOverlayEnabled = uiSettingsMenuDialogCheckboxes[0];
        isShowPlanesEnabled = uiSettingsMenuDialogCheckboxes[1];
        isARCoreDisplayEnabled = uiSettingsMenuDialogCheckboxes[2];
        isConfidenceFullscreen = uiSettingsMenuDialogCheckboxes[3];
        isConfidenceInsetEnabled = uiSettingsMenuDialogCheckboxes[4];
        isHeatmapInsetEnabled = uiSettingsMenuDialogCheckboxes[5];
        isPointCloudVisualizationEnabled = uiSettingsMenuDialogCheckboxes[6];
        isTooCloseVizualizationEnabled = uiSettingsMenuDialogCheckboxes[7];
        isLockingVizualizationEnabled = uiSettingsMenuDialogCheckboxes[8];
        isPhoneCoordinatesEnabled = uiSettingsMenuDialogCheckboxes[9];
        isMovingTooFastViewEnabled = uiSettingsMenuDialogCheckboxes[10];
        isShowAnchorsBelowMinScore = uiSettingsMenuDialogCheckboxes[11];
        isColorHighEntropyBlobsBeforeMinimapCompleteEnabled = uiSettingsMenuDialogCheckboxes[12];
        isHideAnchorsWhileMinimapInProgress = uiSettingsMenuDialogCheckboxes[13];
        isShowIndividualSquareProgressBar = uiSettingsMenuDialogCheckboxes[14];
        isShowAllFovFilterBlobs = uiSettingsMenuDialogCheckboxes[15];
        isShowMLThresholdSeekbar = uiSettingsMenuDialogCheckboxes[16];
        isWaitToCreateReferenceAnchors = uiSettingsMenuDialogCheckboxes[17];
        isUpdateAnchorPositionOverTime = uiSettingsMenuDialogCheckboxes[18];
        isDisplayAllEntropyPoses = uiSettingsMenuDialogCheckboxes[19];
        isDisplayLockedEntropyPoses = uiSettingsMenuDialogCheckboxes[20];
        isShowAllScannedDistancesAsGreen = uiSettingsMenuDialogCheckboxes[21];

        isRemovingSmallAnchors = filter3DSettings3MenuDialogCheckboxes[0];
        isDecayingAnchors = filter3DSettings3MenuDialogCheckboxes[1];
        isFOVFiltering = filter3DSettings3MenuDialogCheckboxes[2];


        // We aren't putting our autofocus settings here because we want it to be non persistent
        // Autofocus should be off (recommended by ARCore) unless really necessary for a bit
        configureSession();
    }

    private void resetSettingsMenuDialogCheckboxes() {
        depthSettingsMenuDialogCheckboxes[0] = depthSettings.useDepthForOcclusion();
        depthSettingsMenuDialogCheckboxes[1] = depthSettings.depthColorVisualizationEnabled();
        instantPlacementSettingsMenuDialogCheckboxes[0] =
                instantPlacementSettings.isInstantPlacementEnabled();
        autoFocusSettingsMenuDialogCheckboxes[0] = isAutoFocusEnabled;

        uiSettingsMenuDialogCheckboxes[0] = isDepthOverlayEnabled;
        uiSettingsMenuDialogCheckboxes[1] = isShowPlanesEnabled;
        uiSettingsMenuDialogCheckboxes[2] = isARCoreDisplayEnabled;
        uiSettingsMenuDialogCheckboxes[3] = isConfidenceFullscreen;
        uiSettingsMenuDialogCheckboxes[4] = isConfidenceInsetEnabled;
        uiSettingsMenuDialogCheckboxes[5] = isHeatmapInsetEnabled;
        uiSettingsMenuDialogCheckboxes[6] = isPointCloudVisualizationEnabled;
        uiSettingsMenuDialogCheckboxes[7] = isTooCloseVizualizationEnabled;
        uiSettingsMenuDialogCheckboxes[8] = isLockingVizualizationEnabled;
        uiSettingsMenuDialogCheckboxes[9] = isPhoneCoordinatesEnabled;
        uiSettingsMenuDialogCheckboxes[10] = isMovingTooFastViewEnabled;
        uiSettingsMenuDialogCheckboxes[11] = isShowAnchorsBelowMinScore;
        uiSettingsMenuDialogCheckboxes[12] = isColorHighEntropyBlobsBeforeMinimapCompleteEnabled;
        uiSettingsMenuDialogCheckboxes[13] = isHideAnchorsWhileMinimapInProgress;
        uiSettingsMenuDialogCheckboxes[14] = isShowIndividualSquareProgressBar;
        uiSettingsMenuDialogCheckboxes[15] = isShowAllFovFilterBlobs;
        uiSettingsMenuDialogCheckboxes[16] = isShowMLThresholdSeekbar;
        uiSettingsMenuDialogCheckboxes[17] = isWaitToCreateReferenceAnchors;
        uiSettingsMenuDialogCheckboxes[18] = isUpdateAnchorPositionOverTime;
        uiSettingsMenuDialogCheckboxes[19] = isDisplayAllEntropyPoses;
        uiSettingsMenuDialogCheckboxes[20] = isDisplayLockedEntropyPoses;
        uiSettingsMenuDialogCheckboxes[21] = isShowAllScannedDistancesAsGreen;


        filter3DSettings3MenuDialogCheckboxes[0] = isRemovingSmallAnchors;
        filter3DSettings3MenuDialogCheckboxes[1] = isDecayingAnchors;
        filter3DSettings3MenuDialogCheckboxes[2] = isFOVFiltering;
    }

    /**
     * Configures the session with feature settings.
     */
    private void configureSession() {
        Config config = session.getConfig();
        if (isAutoFocusEnabled) {
            config.setFocusMode(Config.FocusMode.AUTO);
        } else {
            config.setFocusMode(Config.FocusMode.FIXED);
        }
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
        if (instantPlacementSettings.isInstantPlacementEnabled()) {
            config.setInstantPlacementMode(InstantPlacementMode.LOCAL_Y_UP);
        } else {
            config.setInstantPlacementMode(InstantPlacementMode.DISABLED);
        }

        if (isConfidenceFullscreen) {
            // Reset bitmap transform to recalculate parameters
            confidenceScaledBitmapTransform = null;
            depthDataView.setVisibility(View.INVISIBLE);
            depthDataViewFull.setVisibility(View.VISIBLE);
        } else if (isConfidenceInsetEnabled) {
            // Reset bitmap transform to recalculate parameters
            confidenceScaledBitmapTransform = null;
            depthDataView.setVisibility(View.VISIBLE);
            depthDataViewFull.setVisibility(View.INVISIBLE);
        } else {
            depthDataView.setVisibility(View.INVISIBLE);
            depthDataViewFull.setVisibility(View.INVISIBLE);
        }

        if (isPhoneCoordinatesEnabled) {
            cameraPoseTextView.setVisibility(View.VISIBLE);
        } else {
            cameraPoseTextView.setVisibility(View.INVISIBLE);
        }

        if (isShowMLThresholdSeekbar) {
            mlSeekBar.setVisibility(View.VISIBLE);
            mlSeekBarTextView.setVisibility(View.VISIBLE);
        } else {
            mlSeekBar.setVisibility(View.INVISIBLE);
            mlSeekBarTextView.setVisibility(View.INVISIBLE);
        }

        // Configure filter options
        // Applying settings after 2 seconds to observe diff
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> CVManager.getInstance().setFilterSettingsFromSettingsArray(filterSettingsMenuDialogCheckboxes), PhoneLidarConfig.SETTINGS_CHANGE_DELAY_MILLIS);

        // We should not disable plane finding mode
        // ARCore seems to work better with the planes
        // Anyways, we should be using instant placement with proper depth hint
        config.setPlaneFindingMode(Config.PlaneFindingMode.VERTICAL);
        session.configure(config);
    }


    /****************************
     * END SETTINGS MENUS
     ****************************/

    /****************************
     * DATA COLLECTION HELPER
     ****************************/
    private void recordNormal(Camera camera) {
        if (DC_isSetPose) {
            DC_VectorNormal = camera.getPose();

            showToast("Normal Updated: " + String.format("|%f %f %f",
                    DC_VectorNormal.getTranslation()[0],
                    DC_VectorNormal.getTranslation()[1],
                    DC_VectorNormal.getTranslation()[2]
            ));
            DC_isSetPose = false;
        }
    }


    /****************************
     * END DATA COLLECTION HELPER
     ****************************/

    /**
     * Uses a single Toast to show text - this prevents stacking of multiple Toasts activated in sequence
     *
     * @param text
     */
    private void showToast(String text) {
        this.runOnUiThread(() -> {
            myToast.cancel();
            myToast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
            myToast.setText(text);
            myToast.show();
        });
    }

    /**
     * Returns a transformation matrix that when applied to screen space uvs makes them match
     * correctly with the quad texture coords used to render the camera feed. It takes into account
     * device orientation.
     */
    private static float[] getTextureTransformMatrix(Frame frame) {
        float[] frameTransform = new float[6];
        float[] uvTransform = new float[9];
        // XY pairs of coordinates in NDC space that constitute the origin and points along the two
        // principal axes.
        float[] ndcBasis = {0, 0, 1, 0, 0, 1};

        // Temporarily store the transformed points into outputTransform.
        frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                ndcBasis,
                Coordinates2d.TEXTURE_NORMALIZED,
                frameTransform);

        // Convert the transformed points into an affine transform and transpose it.
        float ndcOriginX = frameTransform[0];
        float ndcOriginY = frameTransform[1];
        uvTransform[0] = frameTransform[2] - ndcOriginX;
        uvTransform[1] = frameTransform[3] - ndcOriginY;
        uvTransform[2] = 0;
        uvTransform[3] = frameTransform[4] - ndcOriginX;
        uvTransform[4] = frameTransform[5] - ndcOriginY;
        uvTransform[5] = 0;
        uvTransform[6] = ndcOriginX;
        uvTransform[7] = ndcOriginY;
        uvTransform[8] = 1;

        return uvTransform;
    }

    private static Shader createVirtualObjectShader(
            SampleRender render, Texture virtualObjectTexture, boolean useDepthForOcclusion)
            throws IOException {
        return Shader.createFromAssets(
                render,
                AMBIENT_INTENSITY_VERTEX_SHADER_NAME,
                AMBIENT_INTENSITY_FRAGMENT_SHADER_NAME,
                new HashMap<String, String>() {
                    {
                        put("USE_DEPTH_FOR_OCCLUSION", useDepthForOcclusion ? "1" : "0");
                    }
                })
                .setBlend(Shader.BlendFactor.SRC_ALPHA, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA)
                .setTexture("u_AlbedoTexture", virtualObjectTexture)
                .set1("u_UpperDiffuseIntensity", 1.0f)
                .set1("u_LowerDiffuseIntensity", 0.5f)
                .set1("u_SpecularIntensity", 0.2f)
                .set1("u_SpecularPower", 8.0f);
    }


    /**
     * Assign a color to the object for rendering based on the trackable type this anchor attached to.
     * For AR_TRACKABLE_POINT, it's blue color.
     * For AR_TRACKABLE_PLANE, it's green color.
     * For AR_TRACKABLE_INSTANT_PLACEMENT_POINT while tracking method is
     * SCREENSPACE_WITH_APPROXIMATE_DISTANCE, it's white color.
     * For AR_TRACKABLE_INSTANT_PLACEMENT_POINT once tracking method becomes FULL_TRACKING, it's
     * orange color.
     * The color will update for an InstantPlacementPoint once it updates its tracking method from
     * SCREENSPACE_WITH_APPROXIMATE_DISTANCE to FULL_TRACKING.
     */
    private float[] getTrackableColor(Trackable trackable) {
        if (trackable instanceof Point) {
            return new float[]{255.0f / 255.0f, 0.0f / 255.0f, 0.0f / 255.0f};
        }
        if (trackable instanceof Plane) {
            return new float[]{139.0f / 255.0f, 195.0f / 255.0f, 74.0f / 255.0f};
        }
        if (trackable instanceof InstantPlacementPoint) {
            if (((InstantPlacementPoint) trackable).getTrackingMethod()
                    == TrackingMethod.SCREENSPACE_WITH_APPROXIMATE_DISTANCE) {
                return new float[]{50.0f / 255.0f, 255.0f / 255.0f, 50.0f / 255.0f};
            }
            if (((InstantPlacementPoint) trackable).getTrackingMethod() == TrackingMethod.FULL_TRACKING) {
                return new float[]{255.0f / 255.0f, 167.0f / 255.0f, 38.0f / 255.0f};
            }
        }
        // Fallback color.
        return new float[]{0f, 0f, 0f};
    }
}
