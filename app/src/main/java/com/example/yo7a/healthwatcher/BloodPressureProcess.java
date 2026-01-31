package com.example.yo7a.healthwatcher;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

public class BloodPressureProcess extends Activity {

    private static final String TAG = "BloodPressureProcess";
    private static final AtomicBoolean processing = new AtomicBoolean(false);

    // UI
    private ProgressBar progBP;
    private int progP = 0;
    private Toast mainToast;

    // Camera & PPG
    private SurfaceView preview;
    private SurfaceHolder previewHolder;
    private Camera camera;
    private PowerManager.WakeLock wakeLock;

    // User data
    private String user;
    private UserDB Data;
    private int age = 30, height = 170, weight = 70, gender = 0;

    // Buffers & timing
    private final ArrayList<Double> redAvgList = new ArrayList<>();
    private final ArrayList<Double> greenAvgList = new ArrayList<>();
    private long startTime = 0L;
    private int frameCounter = 0;
    private double samplingFreq = 0.0;

    // Result smoothing/history
    private final Deque<Integer> recentHrDeque = new ArrayDeque<>(6);

    // Blood pressure results
    private int SP = 0, DP = 0;

    // Constants & thresholds
    private static final double REQUIRED_SECONDS_MIN = 12.0;
    private static final double REQUIRED_SECONDS_MAX = 30.0;
    private static final int MIN_FRAMES = 40;
    private static final int MIN_HR = 40;
    private static final int MAX_HR = 200;
    private static final double SNR_THRESHOLD = 4.0;
    private static final double CALIBRATION_MULTIPLIER = 1.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blood_pressure_process);

        user = getIntent().getStringExtra("Usr");
        Data = new UserDB(getApplicationContext());

        // FIXED: Assign integers directly
        try { age = Data.getage(user); } catch (Exception ignored) {}
        try { height = Data.getheight(user); } catch (Exception ignored) {}
        try { weight = Data.getweight(user); } catch (Exception ignored) {}
        try { gender = Data.getgender(user); } catch (Exception ignored) {}

        progBP = findViewById(R.id.BPPB);
        if (progBP != null) progBP.setProgress(0);

        preview = findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "HealthWatcher:BP");
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(); } catch (Exception ignored) {}
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
        } catch (Exception e) {
            showShortToast("Camera not available");
            finish();
            return;
        }
        startTime = System.currentTimeMillis();
        resetBuffers();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        releaseCamera();
    }

    private void releaseCamera() {
        if (camera != null) {
            try { camera.setPreviewCallback(null); } catch (Exception ignored) {}
            try { camera.stopPreview(); } catch (Exception ignored) {}
            try { camera.release(); } catch (Exception ignored) {}
            camera = null;
        }
    }

    private final Camera.PreviewCallback previewCallback = (data, cam) -> {
        if (data == null || cam == null) return;
        if (!processing.compareAndSet(false, true)) return;

        try {
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) { processing.set(false); return; }

            double greenAvg = ImageProcessing.decodeYUV420SPtoRedBlueGreenAvg(data, size.width, size.height, 3);
            double redAvg = ImageProcessing.decodeYUV420SPtoRedBlueGreenAvg(data, size.width, size.height, 1);

            if (greenAvg < 35.0 && redAvg < 35.0) {
                if (frameCounter == 0) showShortToast("Place your finger firmly on the camera lens");
                processing.set(false);
                return;
            }

            greenAvgList.add(greenAvg);
            redAvgList.add(redAvg);
            frameCounter++;

            long now = System.currentTimeMillis();
            double elapsedSec = (now - startTime) / 1000.0;
            if (elapsedSec > 0.5) samplingFreq = frameCounter / elapsedSec;

            if (progBP != null) {
                int p = Math.min(100, (int) Math.round(Math.min(elapsedSec / REQUIRED_SECONDS_MAX, 1.0) * 100.0));
                progBP.setProgress(p);
            }

            if (elapsedSec >= REQUIRED_SECONDS_MIN && frameCounter >= MIN_FRAMES) {
                int desiredFrames = (int) Math.round(Math.max(MIN_FRAMES, REQUIRED_SECONDS_MAX * Math.max(1.0, samplingFreq)));
                while (greenAvgList.size() > desiredFrames) {
                    greenAvgList.remove(0);
                    redAvgList.remove(0);
                }
                frameCounter = greenAvgList.size();

                int N = frameCounter;
                if (N < MIN_FRAMES) { processing.set(false); return; }

                double[] samples = new double[N];
                for (int i = 0; i < N; i++) samples[i] = greenAvgList.get(i);

                SignalProcessing.removeLinearTrend(samples);
                SignalProcessing.applyHammingWindow(samples);

                double minHz = 0.7, maxHz = 4.0;
                double[] outSNR = new double[1];
                double freqHz = SignalProcessing.findDominantFrequencyHz(samples, samplingFreq, minHz, maxHz, outSNR);
                double snr = (outSNR != null && outSNR.length > 0) ? outSNR[0] : 0.0;

                if (Double.isNaN(freqHz) || freqHz <= 0 || snr < SNR_THRESHOLD) {
                    for (int i = 0; i < N; i++) samples[i] = redAvgList.get(i);
                    SignalProcessing.removeLinearTrend(samples);
                    SignalProcessing.applyHammingWindow(samples);
                    freqHz = SignalProcessing.findDominantFrequencyHz(samples, samplingFreq, minHz, maxHz, outSNR);
                    snr = (outSNR != null && outSNR.length > 0) ? outSNR[0] : 0.0;
                }

                int hr = (freqHz > 0 && !Double.isNaN(freqHz)) ? (int) Math.round(freqHz * 60.0) : 0;

                if (hr < MIN_HR || hr > MAX_HR || snr < SNR_THRESHOLD) {
                    if (elapsedSec >= REQUIRED_SECONDS_MAX) {
                        showShortToast("Measurement failed — reposition finger and try again");
                        resetBuffers();
                        startTime = System.currentTimeMillis();
                    }
                    processing.set(false);
                    return;
                }

                if (recentHrDeque.size() == 6) recentHrDeque.pollFirst();
                recentHrDeque.offerLast(hr);
                int finalHr = medianOfDeque(recentHrDeque);

                double Qfactor = (gender == 1) ? 5.0 : 4.5;
                double ROB = 18.5;
                double ET = 364.5 - 1.23 * finalHr;
                double BSA = 0.007184 * Math.pow(weight, 0.425) * Math.pow(height, 0.725);
                double SV = -6.6 + 0.25 * (ET - 35) - 0.62 * finalHr + 40.4 * BSA - 0.51 * age;
                if (Double.isNaN(SV) || SV <= 0) SV = Math.max(20.0, SV);
                double PP = SV / ((0.013 * weight - 0.007 * age - 0.004 * finalHr) + 1.307);
                if (Double.isNaN(PP) || PP <= 0) PP = 30.0;
                double MPP = Qfactor * ROB;
                double spRaw = (MPP + 1.5 * PP) * CALIBRATION_MULTIPLIER;
                double dpRaw = (MPP - PP / 3.0) * CALIBRATION_MULTIPLIER;
                SP = (int) Math.round(spRaw);
                DP = (int) Math.round(dpRaw);

                if (SP < 70 || SP > 260 || DP < 40 || DP > 180) {
                    if (elapsedSec >= REQUIRED_SECONDS_MAX) {
                        showShortToast("BP estimation failed — try again");
                        resetBuffers();
                        startTime = System.currentTimeMillis();
                    }
                    processing.set(false);
                    return;
                }

                double snrScore = Math.min(1.0, snr / (SNR_THRESHOLD * 2.0));
                double hrStability = computeStabilityScore(recentHrDeque);
                double confidence = 0.6 * snrScore + 0.4 * hrStability;

                if (confidence < 0.45 && elapsedSec < REQUIRED_SECONDS_MAX) {
                    processing.set(false);
                    return;
                }

                new Thread(() -> {
                    try {
                        int local = progP;
                        while (local <= 100) {
                            final int val = local;
                            runOnUiThread(() -> {
                                if (progBP != null) progBP.setProgress(val);
                            });
                            Thread.sleep(15);
                            local++;
                        }
                    } catch (InterruptedException ignored) {}
                }).start();

                resetBuffers();
                processing.set(false);
            }

        } catch (Exception e) {
            Log.e(TAG, "Frame processing error", e);
        } finally {
            processing.set(false);
        }
    };

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                if (camera != null) {
                    camera.setPreviewDisplay(previewHolder);
                    camera.setPreviewCallback(previewCallback);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Exception in setPreviewDisplay()", t);
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (camera == null) return;
            try {
                Camera.Parameters params = camera.getParameters();
                params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                Camera.Size size = getSmallestPreviewSize(width, height, params);
                if (size != null) params.setPreviewSize(size.width, size.height);
                camera.setParameters(params);
                camera.startPreview();
            } catch (Exception e) {
                Log.e(TAG, "Error starting preview", e);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {}
    };

    private static Camera.Size getSmallestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null || size.width * size.height < result.width * result.height)
                    result = size;
            }
        }
        return result;
    }

    private void resetBuffers() {
        redAvgList.clear();
        greenAvgList.clear();
        frameCounter = 0;
        startTime = System.currentTimeMillis();
        samplingFreq = 0.0;
        recentHrDeque.clear();
        progP = 0;
        if (progBP != null) progBP.setProgress(0);
    }

    private int medianOfDeque(Deque<Integer> dq) {
        if (dq.isEmpty()) return 0;
        int n = dq.size();
        int[] arr = new int[n];
        int i = 0;
        for (int v : dq) arr[i++] = v;
        java.util.Arrays.sort(arr);
        return arr[n / 2];
    }

    private double computeStabilityScore(Deque<Integer> dq) {
        if (dq.isEmpty()) return 0.0;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int v : dq) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double span = Math.max(1.0, max - min);
        return Math.max(0.0, 1.0 - (span / 10.0));
    }

    private void showShortToast(String msg) {
        if (mainToast != null) mainToast.cancel();
        mainToast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        mainToast.show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    public int getSP() { return SP; }
    public int getDP() { return DP; }
}
