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

public class HeartRateProcess extends Activity {

    private static final String TAG = "HeartRateProcess";
    private static final AtomicBoolean processing = new AtomicBoolean(false);

    private SurfaceView preview;
    private SurfaceHolder previewHolder;
    private Camera camera;
    private PowerManager.WakeLock wakeLock;
    private ProgressBar progHR;
    private Toast mainToast;
    private String user;

    private final ArrayList<Double> greenList = new ArrayList<>();
    private final ArrayList<Double> redList = new ArrayList<>();
    private long startTime;
    private int frameCount = 0;
    private double samplingFreq = 0.0;

    private final Deque<Integer> recentBpms = new ArrayDeque<>(6);
    private double emaBpm = -1.0;

    private static final double MIN_SECONDS = 8.0;
    private static final double MAX_SECONDS = 30.0;
    private static final int MIN_FRAMES = 40;
    private static final int MIN_BPM = 40;
    private static final int MAX_BPM = 200;
    private static final double SNR_THRESHOLD = 4.0;
    private static final double STABLE_DELTA = 3.0;
    private static final int STABLE_COUNT = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate_process);

        user = getIntent().getStringExtra("Usr");

        preview = findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);

        progHR = findViewById(R.id.HRPB);
        if (progHR != null) progHR.setProgress(0);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "HealthWatcher:HR");
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(); } catch (Exception ignored) {}
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
        } catch (Exception e) {
            showToast("Camera not available");
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

            double green = ImageProcessing.decodeYUV420SPtoRedBlueGreenAvg(data, size.width, size.height, 3);
            double red = ImageProcessing.decodeYUV420SPtoRedBlueGreenAvg(data, size.width, size.height, 1);

            if (green < 35 || red < 35) {
                if (frameCount == 0) showToast("Place finger firmly on camera");
                processing.set(false);
                return;
            }

            greenList.add(green);
            redList.add(red);
            frameCount++;

            double elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0;
            samplingFreq = frameCount / Math.max(elapsedSec, 0.001);

            // Update progress
            if (progHR != null) {
                int p = Math.min(100, (int) Math.round(Math.min(elapsedSec / MAX_SECONDS, 1.0) * 100.0));
                progHR.setProgress(p);
            }

            if (elapsedSec >= MIN_SECONDS && frameCount >= MIN_FRAMES) {
                // Limit window to MAX_SECONDS
                int maxFrames = (int)(MAX_SECONDS * samplingFreq);
                while (greenList.size() > maxFrames) { greenList.remove(0); redList.remove(0); frameCount--; }

                double[] samples = greenList.stream().mapToDouble(d -> d).toArray();
                SignalProcessing.removeLinearTrend(samples);
                SignalProcessing.applyHammingWindow(samples);

                double[] outSNR = new double[1];
                double freqHz = SignalProcessing.findDominantFrequencyHz(samples, samplingFreq, 0.7, 4.0, outSNR);
                double snr = outSNR[0];

                if (Double.isNaN(freqHz) || freqHz <= 0 || snr < SNR_THRESHOLD) {
                    samples = redList.stream().mapToDouble(d -> d).toArray();
                    SignalProcessing.removeLinearTrend(samples);
                    SignalProcessing.applyHammingWindow(samples);
                    freqHz = SignalProcessing.findDominantFrequencyHz(samples, samplingFreq, 0.7, 4.0, outSNR);
                    snr = outSNR[0];
                }

                int bpm = (freqHz > 0) ? (int)Math.round(freqHz * 60.0) : 0;
                if (bpm < MIN_BPM || bpm > MAX_BPM || snr < SNR_THRESHOLD) {
                    if (elapsedSec >= MAX_SECONDS) showToast("Measurement failed — reposition finger");
                    processing.set(false);
                    return;
                }

                emaBpm = (emaBpm < 0) ? bpm : 0.45 * bpm + 0.55 * emaBpm;

                if (recentBpms.size() == 6) recentBpms.pollFirst();
                recentBpms.offerLast((int)Math.round(emaBpm));

                int finalBpm = medianOfDeque(recentBpms);
                if (checkStability(recentBpms, STABLE_DELTA, STABLE_COUNT) || elapsedSec >= MAX_SECONDS) {
                    Intent i = new Intent(HeartRateProcess.this, HeartRateResult.class);
                    i.putExtra("BPM", finalBpm);
                    i.putExtra("Usr", user);
                    startActivity(i);
                    finish();
                    resetBuffers();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Preview callback error", e);
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
            } catch (Exception e) { Log.e(TAG, "Preview setup error", e); }
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
            } catch (Exception e) { Log.e(TAG, "Camera preview error", e); }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {}
    };

    private static Camera.Size getSmallestPreviewSize(int width, int height, Camera.Parameters params) {
        Camera.Size result = null;
        for (Camera.Size s : params.getSupportedPreviewSizes()) {
            if (s.width <= width && s.height <= height) {
                if (result == null || s.width * s.height < result.width * result.height) result = s;
            }
        }
        return result;
    }

    private void resetBuffers() {
        greenList.clear();
        redList.clear();
        frameCount = 0;
        startTime = System.currentTimeMillis();
        samplingFreq = 0;
        recentBpms.clear();
        emaBpm = -1;
        if (progHR != null) progHR.setProgress(0);
    }

    private void showToast(String msg) {
        if (mainToast != null) mainToast.cancel();
        mainToast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        mainToast.show();
    }

    @Override
    public void onBackPressed() {
        Intent i = new Intent(this, StartVitalSigns.class);
        i.putExtra("Usr", user);
        startActivity(i);
        finish();
    }

    private int medianOfDeque(Deque<Integer> dq) {
        int n = dq.size();
        int[] arr = new int[n];
        int idx = 0;
        for (int v : dq) arr[idx++] = v;
        java.util.Arrays.sort(arr);
        return arr[n/2];
    }

    private boolean checkStability(Deque<Integer> dq, double delta, int count) {
        if (dq.size() < count) return false;
        Integer[] arr = dq.toArray(new Integer[0]);
        int len = arr.length;
        int start = len - count;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = start; i < len; i++) { min = Math.min(min, arr[i]); max = Math.max(max, arr[i]); }
        return (max - min) <= delta;
    }
}


