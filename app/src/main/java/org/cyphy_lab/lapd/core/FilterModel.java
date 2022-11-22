package org.cyphy_lab.lapd.core;

import android.app.Activity;
import android.util.Log;

import com.cyphy_lab.lapd.BuildConfig;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;


public class FilterModel {
    private static final String TAG = FilterModel.class.getSimpleName();

    private static final TensorBuffer inputBuffer =
            TensorBuffer.createFixedSize(new int[]{1, 5, 5, 2}, DataType.FLOAT32);

    // Creates the output tensor and its processor (1 sample, 1 class)
    private static final TensorBuffer outputProbabilityBuffer =
            TensorBuffer.createFixedSize(new int[]{1, 1}, DataType.FLOAT32);

    // Positive example 1 (0-indexed) from training set
    private static final float[] testImage = new float[]{
            0.3f, 0.45f, 0.4f, 0.45f, 0.25f, 0.5f, 0.6f, 0.55f, 0.5f, 0.4f, 0.4f, 1.0f, 0.15f, 0.4f, 0.5f, 0.5f, 0.6f, 0.8f, 0.5f, 0.7f, 0.4f, 0.55f, 0.6f, 0.6f, 0.35f,
            0.42857143f, 0.42857143f, 0.42857143f, 0.42857143f, 0.42857143f, 0.42857143f, 0.5714286f, 0.5714286f, 0.42857143f, 0.42857143f, 0.42857143f, 0.85714287f, 1.0f, 0.5714286f, 0.42857143f, 0.42857143f, 0.5714286f, 0.5714286f, 0.42857143f, 0.42857143f, 0.42857143f, 0.42857143f, 0.42857143f, 0.42857143f, 0.42857143f
    };

    public static final String[] MODEL_NAMES = new String[]{
            "saved_model.tflite",
            "model1.tflite",
            "model2.tflite",
            "model3.tflite",
            "model4.tflite"};

    private static final float[] MODEL_EXPECTED_PROBABILITIES = new float[]{
            0.86942315f,
            0.46816266f,
            0.6353097f,
            0.5985001f,
            0.03152752f,
    };

    /**
     * CHANGE THIS TO CHANGE THE DEFAULT ACTIVE MODEL
     */
    public static int currentModelIndex = 0;

    private static final List<Interpreter> interpreterList = new ArrayList<>();

    public FilterModel(Activity activity) {
        // Initialize interpreter with GPU delegate if possible
        Interpreter.Options options = new Interpreter.Options();
        // Right now, CPU is WAY faster
        options.setNumThreads(2);

        // After GPU/CPU selection, load the tflite moad to mapped buffer to reduce dirty pages
        try {
            for (int i = 0; i < MODEL_NAMES.length; i++) {
                MappedByteBuffer model = FileUtil.loadMappedFile(activity,
                        MODEL_NAMES[i]);
                Interpreter interpreter = new Interpreter(model, options);
                interpreterList.add(interpreter);
            }
        } catch (IOException ex) {
            Log.e(TAG, "Could not open tflite model!", ex);
            if (BuildConfig.DEBUG) {
                throw new AssertionError("Assertion failed");
            }
        }
    }

    public static void runInferenceTest() {
        inputBuffer.loadArray(testImage);
        for (int i = 0; i < MODEL_NAMES.length; i++) {
            float testProbability = runInference(inputBuffer.getBuffer(), i);
            Log.d(TAG, "TEST PROBABILITY FOR IDX: " + i + " = " + testProbability);
            if (BuildConfig.DEBUG && testProbability != MODEL_EXPECTED_PROBABILITIES[i]) {
                throw new AssertionError("Test probabilities not equal: " + testProbability + " != " + MODEL_EXPECTED_PROBABILITIES[i]);
            }
        }

    }

    public static float runInferenceImage(float[] image) {
        inputBuffer.loadArray(image);
        return runInference(inputBuffer.getBuffer(), currentModelIndex);
    }

    public static void setModelIndex(int index) {
        currentModelIndex = index;
        Log.w(TAG, "Model changed dynamically to index: " + currentModelIndex + " and name " + MODEL_NAMES[currentModelIndex]);
    }

    private static float runInference(Object inputBuffer, int modelIdx) {
        interpreterList.get(modelIdx).run(inputBuffer, outputProbabilityBuffer.getBuffer().rewind());
        float probability = outputProbabilityBuffer.getFloatArray()[0];
//        Log.i(TAG, "Finished probability prediction: " + probability);
        return probability;
    }
}