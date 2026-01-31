package com.example.yo7a.healthwatcher;

import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;

public class GlucoseEstimator {

    private static final String TAG = "GlucoseEstimator";

    // Smoothing parameters
    private static final int SMOOTHING_WINDOW = 10;

    // Red channel bounds
    private static final double RED_MIN = 50.0;
    private static final double RED_MAX = 200.0;

    // BPM bounds
    private static final int BPM_MIN = 50;
    private static final int BPM_MAX = 180;

    // Glucose clamping range
    private static final double GLUCOSE_MIN = 70.0;
    private static final double GLUCOSE_MAX = 180.0;

    // History queues for smoothing
    private static final Queue<Double> redHistory = new LinkedList<>();
    private static final Queue<Integer> bpmHistory = new LinkedList<>();

    // Last stable glucose value for smooth transitions
    private static double lastStableValue = 100.0;

    /** Reset estimator for fresh measurement */
    public static void reset() {
        redHistory.clear();
        bpmHistory.clear();
        lastStableValue = 100.0;
    }

    /** Smooths a double value using a sliding window */
    private static double smoothValue(double newValue, Queue<Double> history, int windowSize) {
        if (history.size() >= windowSize) history.poll();
        history.offer(newValue);

        double sum = 0;
        for (double v : history) sum += v;
        return sum / history.size();
    }

    /** Smooths an integer value using a sliding window */
    private static int smoothValueInt(int newValue, Queue<Integer> history, int windowSize) {
        if (history.size() >= windowSize) history.poll();
        history.offer(newValue);

        int sum = 0;
        for (int v : history) sum += v;
        return sum / history.size();
    }

    /** Estimate glucose contribution from red channel (physiological mapping) */
    private static double estimateFromRed(double redAvg) {
        redAvg = Math.max(RED_MIN, Math.min(redAvg, RED_MAX));
        double normalized = (redAvg - RED_MIN) / (RED_MAX - RED_MIN);
        return 70 + (1 / (1 + Math.exp(-6 * (normalized - 0.5)))) * 100;
    }

    /** Estimate glucose contribution from BPM */
    private static double estimateFromBPM(int bpm) {
        bpm = Math.max(BPM_MIN, Math.min(bpm, BPM_MAX));
        return 80 + Math.pow((bpm - 60) / 120.0, 1.2) * 70;
    }

    /** Combine red and BPM with adaptive weighting and meal factor */
    public static double estimateCombinedGlucose(double redAvg, int bpm, double mealFactor) {
        double smoothedRed = smoothValue(redAvg, redHistory, SMOOTHING_WINDOW);
        int smoothedBPM = smoothValueInt(bpm, bpmHistory, SMOOTHING_WINDOW);

        double glucoseRed = estimateFromRed(smoothedRed);
        double glucoseBPM = estimateFromBPM(smoothedBPM);

        double variation = Math.abs(glucoseRed - lastStableValue);
        double weightRed = variation < 5 ? 0.7 : 0.5;
        double weightBPM = 1.0 - weightRed;

        double glucose = (weightRed * glucoseRed + weightBPM * glucoseBPM) * mealFactor;
        glucose = Math.max(GLUCOSE_MIN, Math.min(glucose, GLUCOSE_MAX));
        glucose = (lastStableValue * 0.6) + (glucose * 0.4);
        lastStableValue = glucose;

        Log.d(TAG, String.format("Red: %.2f, BPM: %d, Glucose: %.2f", smoothedRed, smoothedBPM, glucose));
        return glucose;
    }

    /** ----------------- NEW METHODS FOR MAINACTIVITY ----------------- */

    // Estimate glucose from SpO2 (simple linear mapping)
    public static double estimateGlucoseFromSpO2(double spo2, double mealFactor) {
        // Clamp SpO2 between 90-100%
        spo2 = Math.max(90.0, Math.min(spo2, 100.0));
        double glucose = 120.0 - (spo2 - 90.0) * 2.0; // lower SpO2 -> higher glucose
        glucose *= mealFactor;
        return Math.max(GLUCOSE_MIN, Math.min(glucose, GLUCOSE_MAX));
    }

    // Estimate glucose from infrared PPG (simple fuzzy logic mapping)
    public static double estimateGlucoseFuzzy(double vIR, double mealFactor) {
        // Clamp vIR
        vIR = Math.max(0.0, Math.min(vIR, 200.0));
        double glucose = 70.0 + vIR * 0.5; // arbitrary scaling for demo
        glucose *= mealFactor;
        return Math.max(GLUCOSE_MIN, Math.min(glucose, GLUCOSE_MAX));
    }
}

