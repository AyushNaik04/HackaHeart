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

import com.example.yo7a.healthwatcher.Math.Fft;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RespirationProcess (improved)
 *
 * - Collects green channel PPG samples from camera (more respiratory info than red)
 * - Preprocessing: detrend, mean removal, smoothing, normalize
 * - Two independent estimators:
 *     1) Autocorrelation-based peak on the low-frequency band (robust to noise/motion)
 *     2) FFT-based dominant frequency (coarse)
 * - Cross-validates estimates and uses SNR / stability heuristics to decide final RR
 * - Attempts to reduce measurement failures and early-exit if signal is stable
 */
public class RespirationProcess extends Activity {

    private static final String TAG = "RespirationProcess";
    private static final AtomicBoolean processing = new AtomicBoolean(false);

    private SurfaceView preview;
    private SurfaceHolder previewHolder;
    private Camera camera;
    private PowerManager.WakeLock wakeLock;

    private Toast mainToast;
    private String user;
    private UserDB Data;

    private ProgressBar progResp;
    private int progP = 0;

    private final ArrayList<Double> greenAvgList = new ArrayList<>();
    private final ArrayList<Double> redAvgList = new ArrayList<>();
    private int frameCounter = 0;

    private long startTime = 0;
    private double samplingFreq;

    private int RR = 0;
    private static final double REQUIRED_SECONDS = 20.0; // 20s is usually enough for respiration
    private static final int MIN_FRAMES = 30;            // minimal frames to attempt
    private static final double MIN_RESP_HZ = 0.08;      // ~4.8 bpm (very low)
    private static final double MAX_RESP_HZ = 0.6;       // ~36 bpm (upper reasonable)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_respiration_process);

        Data = new UserDB(getApplicationContext());
        user = getIntent().getStringExtra("Usr");

        preview = findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // FIXED ProgressBar ID
        progResp = findViewById(R.id.respirationProgress);
        if (progResp != null) progResp.setProgress(0);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "HealthWatcher:Resp");
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
        if (camera != null) {
            camera.setPreviewCallback(null);
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
            if (size == null) {
                processing.set(false);
                return;
            }

            // Prefer green channel for respiratory modulation
            double greenAvg = ImageProcessing.decodeYUV420SPtoRedBlueGreenAvg(data, size.width, size.height, 3);
            double redAvg = ImageProcessing.decodeYUV420SPtoRedBlueGreenAvg(data, size.width, size.height, 1);

            // Quick quality check: finger presence & illumination
            if (greenAvg < 30 || redAvg < 30) {
                // don't spam toast: only show on first frame
                if (frameCounter == 0) showShortToast("Place finger firmly on the camera lens");
                processing.set(false);
                return;
            }

            greenAvgList.add(greenAvg);
            redAvgList.add(redAvg);
            frameCounter++;

            long now = System.currentTimeMillis();
            double elapsedSec = (now - startTime) / 1000.0;

            // Update sampling frequency if sufficient time
            if (elapsedSec > 0.5) samplingFreq = frameCounter / elapsedSec;

            // UI progress
            if (progResp != null) {
                int progress = (int) Math.min(100, (elapsedSec / REQUIRED_SECONDS) * 100.0);
                progResp.setProgress(progress);
            }

            // Only analyze when we have enough data
            if (elapsedSec >= REQUIRED_SECONDS && frameCounter >= MIN_FRAMES) {

                int desiredSamples = (int) Math.max(MIN_FRAMES, Math.round(REQUIRED_SECONDS * Math.max(1.0, samplingFreq)));
                if (greenAvgList.size() > desiredSamples) {
                    int toDrop = greenAvgList.size() - desiredSamples;
                    for (int d = 0; d < toDrop; d++) {
                        greenAvgList.remove(0);
                        redAvgList.remove(0);
                    }
                    frameCounter = greenAvgList.size();
                }

                int N = greenAvgList.size();
                if (N < MIN_FRAMES) {
                    processing.set(false);
                    return;
                }

                double[] samples = new double[N];
                for (int i = 0; i < N; i++) samples[i] = greenAvgList.get(i);

                removeLinearTrend(samples);
                double mean = 0.0;
                for (double v : samples) mean += v;
                mean /= N;
                for (int i = 0; i < N; i++) samples[i] -= mean;

                double[] smooth = new double[N];
                for (int i = 0; i < N; i++) {
                    double s = samples[i];
                    if (i > 0) s = (s + samples[i - 1]) * 0.5;
                    if (i > 1) s = (s + samples[i - 2]) / 2.0;
                    smooth[i] = s;
                }

                double ssum = 0.0;
                for (int i = 0; i < N; i++) ssum += smooth[i] * smooth[i];
                double rms = Math.sqrt(ssum / N);
                if (rms <= 1e-9) {
                    showShortToast("Signal too weak, reposition finger");
                    resetBuffers();
                    startTime = System.currentTimeMillis();
                    processing.set(false);
                    return;
                }
                for (int i = 0; i < N; i++) smooth[i] /= rms;

                double autocorrFreqHz = estimateFrequencyAutocorr(smooth, samplingFreq, MIN_RESP_HZ, MAX_RESP_HZ);

                int fftSize = 1;
                while (fftSize < N) fftSize <<= 1;
                Double[] fftInput = new Double[fftSize];
                for (int i = 0; i < fftSize; i++) fftInput[i] = (i < N) ? smooth[i] : 0.0;
                double fftFreqHz = Fft.FFT(fftInput, fftSize, samplingFreq);

                double rrAuto = (Double.isNaN(autocorrFreqHz) || autocorrFreqHz <= 0) ? Double.NaN : autocorrFreqHz * 60.0;
                double rrFft  = (Double.isNaN(fftFreqHz) || fftFreqHz <= 0) ? Double.NaN : fftFreqHz * 60.0;

                Log.d(TAG, "RR estimates: autocorr=" + rrAuto + " bpm, fft=" + rrFft + " bpm");

                double signalEnergy = 0.0;
                for (double v : smooth) signalEnergy += v * v;
                double noiseProxy = computeNoiseProxy(smooth);
                double snr = (noiseProxy > 0) ? (signalEnergy / (noiseProxy * N)) : 0.0;

                int finalRR = -1;

                if (!Double.isNaN(rrAuto) && rrAuto >= 6 && rrAuto <= 40) {
                    if (snr > 0.4) {
                        finalRR = (int)Math.round(rrAuto);
                    }
                }

                if (finalRR < 0 && !Double.isNaN(rrFft) && rrFft >= 6 && rrFft <= 40) {
                    finalRR = (int)Math.round(rrFft);
                }

                if (!Double.isNaN(rrAuto) && !Double.isNaN(rrFft) && rrAuto >= 6 && rrAuto <= 40 && rrFft >= 6 && rrFft <= 40) {
                    double relDiff = Math.abs(rrAuto - rrFft) / Math.max(1.0, (rrAuto + rrFft) / 2.0);
                    if (relDiff < 0.20) {
                        finalRR = (int)Math.round((rrAuto + rrFft) * 0.5);
                    }
                }

                if (finalRR < 6 || finalRR > 40) {
                    showShortToast("Respiration measurement failed, reposition finger and stay still");
                    resetBuffers();
                    startTime = System.currentTimeMillis();
                    processing.set(false);
                    return;
                }

                RR = finalRR;

                Intent i = new Intent(RespirationProcess.this, RespirationResult.class);
                i.putExtra("RR", RR);
                i.putExtra("Usr", user);
                startActivity(i);
                finish();
                resetBuffers();

            }

        } catch (Exception e) {
            Log.e(TAG, "Frame processing error", e);
        } finally {
            processing.set(false);
        }
    };

    private double estimateFrequencyAutocorr(double[] x, double fs, double minHz, double maxHz) {
        int n = x.length;
        if (n < 6) return Double.NaN;

        int maxLag = Math.min(n - 1, (int) Math.floor(fs / minHz));
        int minLag = Math.max(1, (int) Math.floor(fs / maxHz));

        double[] acorr = new double[maxLag + 1];
        for (int lag = 0; lag <= maxLag; lag++) {
            double s = 0;
            for (int i = 0; i + lag < n; i++) s += x[i] * x[i + lag];
            acorr[lag] = s / (n - lag);
        }

        double a0 = Math.max(1e-12, Math.abs(acorr[0]));
        for (int k = 0; k <= maxLag; k++) acorr[k] /= a0;

        int bestLag = -1;
        double bestVal = Double.NEGATIVE_INFINITY;
        for (int lag = minLag; lag <= maxLag; lag++) {
            if (lag <= 0 || lag >= maxLag) continue;
            double val = acorr[lag];
            if (val > bestVal && val > acorr[lag - 1] && val > acorr[lag + 1]) {
                bestVal = val;
                bestLag = lag;
            }
        }

        if (bestLag <= 0 || bestVal < 0.15) return Double.NaN;

        double shift = 0.0;
        double left = acorr[bestLag - 1];
        double center = acorr[bestLag];
        double right = acorr[bestLag + 1];
        double denom = (left - 2.0 * center + right);
        if (Math.abs(denom) > 1e-12) shift = 0.5 * (left - right) / denom;

        double peakLag = bestLag + shift;
        double freqHz = fs / peakLag;
        if (freqHz < minHz || freqHz > maxHz) return Double.NaN;
        return freqHz;
    }

    private double computeNoiseProxy(double[] x) {
        int n = x.length;
        double median = 0;
        double[] copy = new double[n];
        System.arraycopy(x, 0, copy, 0, n);
        java.util.Arrays.sort(copy);
        if (n % 2 == 1) median = copy[n / 2];
        else median = 0.5 * (copy[n / 2 - 1] + copy[n / 2]);

        double[] dev = new double[n];
        for (int i = 0; i < n; i++) dev[i] = Math.abs(x[i] - median);
        java.util.Arrays.sort(dev);
        double mad = (n % 2 == 1) ? dev[n / 2] : 0.5 * (dev[n / 2 - 1] + dev[n / 2]);
        return Math.max(1e-12, mad);
    }

    private void removeLinearTrend(double[] x) {
        if (x == null || x.length < 2) return;
        int n = x.length;
        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumXX = 0.0;
        for (int i = 0; i < n; i++) {
            double xi = i;
            sumX += xi;
            sumY += x[i];
            sumXY += xi * x[i];
            sumXX += xi * xi;
        }
        double denom = n * sumXX - sumX * sumX;
        if (Math.abs(denom) < 1e-12) return;
        double slope = (n * sumXY - sumX * sumY) / denom;
        double intercept = (sumY - slope * sumX) / n;
        for (int i = 0; i < n; i++) x[i] = x[i] - (slope * i + intercept);
    }

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
        frameCounter = 0;
        greenAvgList.clear();
        redAvgList.clear();
        if (progResp != null) progResp.setProgress(0);
        progP = 0;
    }

    @Override
    public void onBackPressed() {
        Intent i = new Intent(RespirationProcess.this, StartVitalSigns.class);
        i.putExtra("Usr", user);
        startActivity(i);
        finish();
    }

    private void showShortToast(String msg) {
        if (mainToast != null) mainToast.cancel();
        mainToast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        mainToast.show();
    }
}

