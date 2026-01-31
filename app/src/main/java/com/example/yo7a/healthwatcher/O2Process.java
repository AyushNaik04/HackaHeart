package com.example.yo7a.healthwatcher;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Improved O2Process:
 * - Uses camera PPG (red + blue channels) to estimate SpO2 via AC/DC ratio method.
 * - Extracts pulse (BPM) using robust dominant-frequency detection (SignalProcessing).
 * - Preprocessing: detrend, window, normalize.
 * - Signal quality checks: frame brightness, stability, SNR.
 * - Smoothing across short windows and adaptive stopping rules.
 *
 * Notes:
 * - SpO2 mapping uses a linear calibration (typical approximate mapping).
 *   If you have device-specific calibration coefficients, replace SPO2_A / SPO2_B below.
 */
public class O2Process extends Activity {

    private SurfaceView preview;
    private SurfaceHolder previewHolder;
    private Camera camera;
    private PowerManager.WakeLock wakeLock;

    private Toast mainToast;
    private String user;
    private UserDB Data;

    private ProgressBar progO2;

    private final ArrayList<Double> redAvgList = new ArrayList<>();
    private final ArrayList<Double> blueAvgList = new ArrayList<>();
    private int frameCounter = 0;

    private long startTime = 0;
    private double samplingFreq;

    private static final AtomicBoolean processing = new AtomicBoolean(false);

    // Measurement parameters (tune if needed)
    private static final double MIN_SECONDS = 12.0;         // minimum collection window before analyze
    private static final double MAX_SECONDS = 60.0;         // max allowed collection time
    private static final int MIN_FRAMES = 30;               // minimum frames required
    private static final int SMOOTH_WINDOW = 3;             // moving average on final values
    private static final double SNR_THRESHOLD = 4.0;        // required SNR (peak/noise) to accept
    private static final double STABILITY_STD_MEAN_MIN = 0.002; // too-flat threshold
    private static final double STABILITY_STD_MEAN_MAX = 0.25;  // too-noisy threshold

    // Simple linear SpO2 calibration constants (A - B * R)
    // Typical approximate values; replace with device-specific calibration if available.
    private static final double SPO2_A = 110.0;
    private static final double SPO2_B = 25.0;

    // smoothing buffers
    private final ArrayList<Double> spo2History = new ArrayList<>();
    private final ArrayList<Integer> pulseHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_o2_process);

        Data = new UserDB(getApplicationContext());
        user = getIntent().getStringExtra("Usr");

        preview = findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        progO2 = findViewById(R.id.O2PB);
        if (progO2 != null) progO2.setProgress(0);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "HealthWatcher:O2");
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(); } catch (Exception ignored) {}
        try {
            camera = Camera.open();
            if (camera != null) {
                camera.setDisplayOrientation(90);
            }
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
            if (size == null) return;

            // Get per-frame channel averages
            double redAvg = ImageProcessing.decodeYUV420SPtoRedBlueGreenAvg(data, size.width, size.height, 1);
            double blueAvg = ImageProcessing.decodeYUV420SPtoRedBlueGreenAvg(data, size.width, size.height, 2);

            // Basic frame quality checks
            if (redAvg < 30 || blueAvg < 30) {
                // Too dark / finger not placed; don't accumulate, but show hint once
                if (frameCounter == 0) showShortToast("Place fingertip gently over camera and flash");
                processing.set(false);
                return;
            }

            // Append samples
            redAvgList.add(redAvg);
            blueAvgList.add(blueAvg);
            frameCounter++;

            long now = System.currentTimeMillis();
            double elapsedSec = (now - startTime) / 1000.0;

            // update sampling estimate (after short initial period)
            if (elapsedSec > 0.5) samplingFreq = frameCounter / elapsedSec;

            // update progress
            if (progO2 != null) {
                int p = Math.min(100, (int) Math.round(Math.min(elapsedSec / MAX_SECONDS, 1.0) * 100.0));
                progO2.setProgress(p);
            }

            // Only analyze after a reasonable minimum time to capture AC components
            if (elapsedSec >= MIN_SECONDS && frameCounter >= MIN_FRAMES) {

                // Use last window up to MAX_SECONDS worth of samples
                int desiredFrames = (int) Math.round(Math.max(MIN_FRAMES, Math.min(frameCounter, Math.round(Math.max(1.0, samplingFreq) * Math.min(elapsedSec, MAX_SECONDS)))));
                // keep newest samples if buffer larger
                while (redAvgList.size() > desiredFrames) {
                    redAvgList.remove(0);
                    blueAvgList.remove(0);
                }
                frameCounter = redAvgList.size();

                // Convert to double arrays
                int N = frameCounter;
                double[] redSamples = new double[N];
                double[] blueSamples = new double[N];
                double meanR = 0.0, meanB = 0.0;
                for (int i = 0; i < N; i++) {
                    redSamples[i] = redAvgList.get(i);
                    blueSamples[i] = blueAvgList.get(i);
                    meanR += redSamples[i];
                    meanB += blueSamples[i];
                }
                meanR /= N;
                meanB /= N;

                // Stability metrics
                double varR = 0.0, varB = 0.0;
                for (int i = 0; i < N; i++) {
                    double dr = redSamples[i] - meanR;
                    double db = blueSamples[i] - meanB;
                    varR += dr * dr;
                    varB += db * db;
                }
                double stdR = Math.sqrt(varR / N);
                double stdB = Math.sqrt(varB / N);
                double stabilityR = (meanR > 0.0) ? (stdR / meanR) : 0.0;
                double stabilityB = (meanB > 0.0) ? (stdB / meanB) : 0.0;

                // Reject too-flat or too-noisy signals
                if (stabilityR < STABILITY_STD_MEAN_MIN && stabilityB < STABILITY_STD_MEAN_MIN) {
                    // too flat → maybe no pulsatile signal yet; allow more time up to MAX_SECONDS
                    if (elapsedSec < MAX_SECONDS) {
                        processing.set(false);
                        return;
                    }
                } else if (stabilityR > STABILITY_STD_MEAN_MAX || stabilityB > STABILITY_STD_MEAN_MAX) {
                    showShortToast("Signal noisy — reposition fingertip");
                    resetBuffers();
                    startTime = System.currentTimeMillis();
                    processing.set(false);
                    return;
                }

                // Preprocess for spectral analysis: remove linear trend and window
                // Use helper SignalProcessing utilities if available
                double[] redCopy = redSamples.clone();
                double[] blueCopy = blueSamples.clone();
                SignalProcessing.removeLinearTrend(redCopy);
                SignalProcessing.removeLinearTrend(blueCopy);
                SignalProcessing.applyHammingWindow(redCopy);
                SignalProcessing.applyHammingWindow(blueCopy);

                // zero-pad to power of two
                int fftSize = SignalProcessing.nextPowerOfTwo(N);
                double[] realR = new double[fftSize];
                double[] imagR = new double[fftSize];
                double[] realB = new double[fftSize];
                double[] imagB = new double[fftSize];
                for (int i = 0; i < N; i++) {
                    realR[i] = redCopy[i];
                    realB[i] = blueCopy[i];
                }
                for (int i = N; i < fftSize; i++) {
                    realR[i] = 0.0;
                    realB[i] = 0.0;
                }

                // Run FFT (internal helper)
                FftInternal.fftRadix2(realR, imagR);
                FftInternal.fftRadix2(realB, imagB);

                // magnitude spectra
                double[] magsR = new double[fftSize / 2];
                double[] magsB = new double[fftSize / 2];
                for (int i = 0; i < magsR.length; i++) {
                    magsR[i] = Math.hypot(realR[i], imagR[i]);
                    magsB[i] = Math.hypot(realB[i], imagB[i]);
                }

                // frequency resolution
                double sf = (samplingFreq > 0.0) ? samplingFreq : (N / Math.max(1.0, elapsedSec));
                double freqRes = sf / fftSize;

                // define physiological search band for heart rate: 0.7 Hz (42 bpm) to 4.0 Hz (240 bpm)
                double minHz = 0.7;
                double maxHz = 4.0;
                int minBin = Math.max(1, (int) Math.floor(minHz / freqRes));
                int maxBin = Math.min(magsR.length - 1, (int) Math.ceil(maxHz / freqRes));

                // find peak in red and blue spectra
                int peakR = minBin; double peakMagR = 0.0;
                int peakB = minBin; double peakMagB = 0.0;
                for (int i = minBin; i <= maxBin; i++) {
                    if (magsR[i] > peakMagR) { peakMagR = magsR[i]; peakR = i; }
                    if (magsB[i] > peakMagB) { peakMagB = magsB[i]; peakB = i; }
                }

                // compute SNR-ish: ratio of peak to median of spectrum
                double noiseR = 1e-12; double noiseB = 1e-12;
                double sumR = 0.0, sumB = 0.0; int cnt = 0;
                for (int i = minBin; i <= maxBin; i++) {
                    if (i >= peakR - 2 && i <= peakR + 2) continue;
                    sumR += magsR[i];
                    sumB += magsB[i];
                    cnt++;
                }
                if (cnt > 0) { noiseR = sumR / cnt; noiseB = sumB / cnt; }
                double snrR = peakMagR / Math.max(noiseR, 1e-12);
                double snrB = peakMagB / Math.max(noiseB, 1e-12);

                // Reject if SNR too low
                if (snrR < SNR_THRESHOLD && snrB < SNR_THRESHOLD) {
                    if (elapsedSec < MAX_SECONDS) { processing.set(false); return; }
                }

                // Convert peak bin to frequency and BPM (use sub-bin quadratic interpolation)
                double[] magsForInterp = magsR; int k = peakR;
                double shift = SignalProcessing.quadraticInterp(magsForInterp, k);
                double refinedBin = k + shift;
                double freqHz = refinedBin * freqRes;
                int pulseBpm = (int) Math.round(freqHz * 60.0);

                // fallback: use green channel-based FFT via SignalProcessing.findDominantFrequencyHz if available
                if (pulseBpm < 30 || pulseBpm > 220) {
                    double[] outSNR = new double[1];
                    double freqHzFallback = SignalProcessing.findDominantFrequencyHz(redCopy, sf, minHz, maxHz, outSNR);
                    if (!Double.isNaN(freqHzFallback) && freqHzFallback > 0.1) {
                        pulseBpm = (int) Math.round(freqHzFallback * 60.0);
                    }
                }

                // AC / DC estimation for SpO2:
                // AC estimated as std (after detrend); DC as mean (before detrend)
                double acR = std(redSamples, meanR);
                double acB = std(blueSamples, meanB);
                double dcR = meanR;
                double dcB = meanB;

                if (dcR <= 0 || dcB <= 0) {
                    processing.set(false);
                    return;
                }

                double ratio = (acR / dcR) / (acB / dcB);

                // Map ratio to SpO2 via linear calibration: Spo2 = A - B * ratio
                double spo2 = SPO2_A - SPO2_B * ratio;

                // Sanity clamp and smoothing
                if (spo2 > 100) spo2 = 100;
                if (spo2 < 60) spo2 = 60;

                // compute combined SNR (min of both)
                double combinedSNR = Math.min(snrR, snrB);

                // Only accept if combined SNR good and pulse plausible
                boolean pulseOk = pulseBpm >= 30 && pulseBpm <= 220;
                boolean spo2Ok = combinedSNR >= (SNR_THRESHOLD / 1.5); // slightly lenient
                if (!pulseOk || !spo2Ok) {
                    // allow more time to collect up to MAX_SECONDS
                    if (elapsedSec < MAX_SECONDS) {
                        processing.set(false);
                        return;
                    }
                }

                // Store history and compute moving averages
                addHistory(spo2History, spo2, SMOOTH_WINDOW);
                addHistory(pulseHistory, pulseBpm, SMOOTH_WINDOW);

                double avgSpo2 = avg(spo2History);
                int avgPulse = (int) Math.round(avgInt(pulseHistory));

                // Final sanity clamp
                if (avgSpo2 < 60 || avgSpo2 > 100) {
                    if (elapsedSec < MAX_SECONDS) { processing.set(false); return; }
                }

                // Success: send result to O2Result
                Intent intent = new Intent(O2Process.this, O2Result.class);
                intent.putExtra("o2Value", (int) Math.round(avgSpo2));
                intent.putExtra("pulse", avgPulse);
                intent.putExtra("Usr", user);
                startActivity(intent);
                finish();
                resetBuffers();
                processing.set(false);
                return;
            }

            // if elapsed exceeds MAX_SECONDS and still no valid result, fail gracefully
            if (elapsedSec >= MAX_SECONDS) {
                showShortToast("Measurement timed out, try again");
                resetBuffers();
                startTime = System.currentTimeMillis();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            processing.set(false);
        }
    };

    // small helpers
    private void addHistory(ArrayList<Double> hist, double v, int max) {
        hist.add(v);
        if (hist.size() > max) hist.remove(0);
    }
    private void addHistory(ArrayList<Integer> hist, int v, int max) {
        hist.add(v);
        if (hist.size() > max) hist.remove(0);
    }
    private double avg(ArrayList<Double> hist) {
        if (hist.isEmpty()) return 0.0;
        double s = 0.0;
        for (double v : hist) s += v;
        return s / hist.size();
    }
    private double avgInt(ArrayList<Integer> hist) {
        if (hist.isEmpty()) return 0.0;
        double s = 0.0;
        for (int v : hist) s += v;
        return s / hist.size();
    }
    private double std(double[] arr, double mean) {
        double s = 0.0;
        for (double v : arr) {
            double d = v - mean;
            s += d * d;
        }
        return Math.sqrt(s / arr.length);
    }
    private double std(Double[] arr, double mean) {
        double s = 0.0;
        for (double v : arr) {
            double d = v - mean;
            s += d * d;
        }
        return Math.sqrt(s / arr.length);
    }
    private double std(ArrayList<Double> arr, double mean) {
        double s = 0.0;
        for (double v : arr) {
            double d = v - mean;
            s += d * d;
        }
        return Math.sqrt(s / arr.size());
    }

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                if (camera != null) {
                    camera.setPreviewDisplay(previewHolder);
                    camera.setPreviewCallback(previewCallback);
                }
            } catch (Exception ignored) {}
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
            } catch (Exception ignored) {}
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
        blueAvgList.clear();
        frameCounter = 0;
        startTime = System.currentTimeMillis();
        samplingFreq = 0.0;
        if (progO2 != null) progO2.setProgress(0);
        spo2History.clear();
        pulseHistory.clear();
    }

    @Override
    public void onBackPressed() {
        Intent i = new Intent(O2Process.this, StartVitalSigns.class);
        i.putExtra("Usr", user);
        startActivity(i);
        finish();
    }

    private void showShortToast(String msg) {
        if (mainToast != null) mainToast.cancel();
        mainToast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        mainToast.show();
    }

    // Minimal internal FFT helper (used above) — reuse your SignalProcessing / FftInternals if available.
    // If you already have FftInternal/SignalProcessing.fftRadix2 accessible, you can remove this class and call that method.
    static final class FftInternal {
        static void fftRadix2(double[] real, double[] imag) {
            int n = real.length;
            int levels = 31 - Integer.numberOfLeadingZeros(n);
            if ((1 << levels) != n) throw new IllegalArgumentException("FFT length not power of 2");
            // bit reverse
            for (int i = 0; i < n; i++) {
                int j = Integer.reverse(i) >>> (32 - levels);
                if (j > i) {
                    double tr = real[i]; double ti = imag[i];
                    real[i] = real[j]; imag[i] = imag[j];
                    real[j] = tr; imag[j] = ti;
                }
            }
            // Cooley-Tukey
            for (int size = 2; size <= n; size <<= 1) {
                int half = size >> 1;
                double tableStep = -2.0 * Math.PI / size;
                for (int i = 0; i < n; i += size) {
                    for (int j = 0; j < half; j++) {
                        double angle = j * tableStep;
                        double wr = Math.cos(angle);
                        double wi = Math.sin(angle);
                        double tr = wr * real[i + j + half] - wi * imag[i + j + half];
                        double ti = wi * real[i + j + half] + wr * imag[i + j + half];
                        real[i + j + half] = real[i + j] - tr;
                        imag[i + j + half] = imag[i + j] - ti;
                        real[i + j] += tr;
                        imag[i + j] += ti;
                    }
                }
            }
        }
    }
}


