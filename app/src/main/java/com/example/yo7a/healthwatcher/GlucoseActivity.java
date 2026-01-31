package com.example.yo7a.healthwatcher;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class GlucoseActivity extends AppCompatActivity {

    private TextView textResult, textPulse, textPercent;
    private ProgressBar progGlucose;
    private ImageButton buttonSend;
    private SurfaceView preview;
    private SurfaceHolder previewHolder;
    private Camera camera;
    private PowerManager.WakeLock wakeLock;

    private static final AtomicBoolean processing = new AtomicBoolean(false);
    private final ArrayList<Double> redAvgList = new ArrayList<>();
    private final Queue<Integer> bpmQueue = new LinkedList<>();
    private int frameCounter = 0;
    private long startTime = 0;
    private double samplingFreq;
    private boolean sensing = false;
    private double mealFactor = 1.0;

    private Toast mainToast;
    private int lastBPM = 0;
    private double lastGlucose = 0.0;

    private static final double REQUIRED_SECONDS = 15.0;
    private static final int MIN_FRAMES = 40;
    private static final int PERMISSION_REQUEST_CAMERA = 201;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_glucose);

        // UI bindings
        textResult = findViewById(R.id.text_result);
        textPulse = findViewById(R.id.text_pulse);
        textPercent = findViewById(R.id.text_percent);
        progGlucose = findViewById(R.id.prog_glucose);
        buttonSend = findViewById(R.id.button_send_glucose);
        preview = findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);

        // Background animation
        View rootLayout = findViewById(R.id.root_layout_glucose);
        if (rootLayout != null) {
            Drawable drawable = rootLayout.getBackground();
            if (drawable instanceof AnimationDrawable) {
                AnimationDrawable anim = (AnimationDrawable) drawable;
                anim.setEnterFadeDuration(3000);
                anim.setExitFadeDuration(3000);
                anim.start();
            }
        }

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST_CAMERA);
        }

        // Wake lock to keep screen on
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            int flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
            wakeLock = pm.newWakeLock(flags, "HealthWatcher:Glucose");
        }

        // Send result via email
        buttonSend.setOnClickListener(v -> sendResultEmail());

        startSensing();
    }

    private void startSensing() {
        sensing = true;
        resetBuffers();
        GlucoseEstimator.reset();
        startTime = System.currentTimeMillis();
        if (camera != null) camera.setPreviewCallback(previewCallback);
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
    }

    private void stopSensing() {
        sensing = false;
        resetBuffers();
        if (camera != null) camera.setPreviewCallback(null);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private final Camera.PreviewCallback previewCallback = (data, cam) -> {
        if (data == null || cam == null || !processing.compareAndSet(false, true)) return;

        try {
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) return;

            double redAvg = ImageProcessing.decodeYUV420SPtoRedBlueGreenAvg(data, size.width, size.height, 1);
            if (redAvg < 50) { processing.set(false); return; }

            redAvgList.add(redAvg);
            frameCounter++;

            double smoothedRed = redAvgList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            long now = System.currentTimeMillis();
            double elapsedSec = (now - startTime) / 1000.0;

            runOnUiThread(() -> {
                int progress = (int) Math.min(100, (elapsedSec / REQUIRED_SECONDS) * 100);
                progGlucose.setProgress(progress);
                textPercent.setText(progress + "%");
            });

            if (elapsedSec >= REQUIRED_SECONDS && frameCounter >= MIN_FRAMES) {
                samplingFreq = frameCounter / elapsedSec;
                int fftSize = 1; while (fftSize < frameCounter) fftSize <<= 1;
                double[] redArr = new double[fftSize];
                for (int i = 0; i < fftSize; i++) redArr[i] = i < redAvgList.size() ? redAvgList.get(i) : 0.0;

                int bpm = (int) Math.round(Fft.FFT(redArr) * 60.0);
                bpmQueue.add(bpm);
                if (bpmQueue.size() > 5) bpmQueue.poll();
                int avgBPM = Math.round((float) bpmQueue.stream().mapToInt(Integer::intValue).sum() / bpmQueue.size());

                double glucose = GlucoseEstimator.estimateCombinedGlucose(smoothedRed, avgBPM, mealFactor);

                lastBPM = avgBPM;
                lastGlucose = glucose;

                runOnUiThread(() -> {
                    textResult.setText(String.format("Estimated Glucose: %.2f mg/dL", glucose));
                    textPulse.setText("Pulse: " + avgBPM + " BPM");
                });

                stopSensing();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            processing.set(false);
        }
    };

    private void sendResultEmail() {
        if (lastGlucose == 0.0) {
            showToast("No measurement available yet.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("message/rfc822");
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{"recipient@example.com"});
        i.putExtra(Intent.EXTRA_SUBJECT, "Health Watcher Glucose Report");
        i.putExtra(Intent.EXTRA_TEXT,
                String.format("Estimated Glucose: %.2f mg/dL\nPulse: %d BPM", lastGlucose, lastBPM));
        try { startActivity(Intent.createChooser(i, "Send mail...")); }
        catch (android.content.ActivityNotFoundException ex) { showToast("No email clients installed."); }
    }

    private void resetBuffers() {
        redAvgList.clear();
        bpmQueue.clear();
        frameCounter = 0;
        if (progGlucose != null) {
            progGlucose.setProgress(0);
            textPercent.setText("0%");
        }
    }

    private void showToast(String msg) {
        if (mainToast != null) mainToast.cancel();
        mainToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        mainToast.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSensing();
    }

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                if (camera == null) camera = Camera.open();
                camera.setDisplayOrientation(90);
                camera.setPreviewDisplay(previewHolder);
            } catch (Exception e) { showToast("Camera not available"); }
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
            } catch (Exception e) { e.printStackTrace(); }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (camera != null) {
                camera.stopPreview();
                camera.release();
                camera = null;
            }
        }
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

    /** Minimal FFT helper class for the activity */
    public static class Fft {
        // Simple peak frequency detection (placeholder)
        public static double FFT(double[] data) {
            if (data.length == 0) return 0.0;
            double max = data[0];
            int maxIndex = 0;
            for (int i = 1; i < data.length; i++) {
                if (data[i] > max) { max = data[i]; maxIndex = i; }
            }
            // frequency estimation placeholder: index / length
            return (double) maxIndex / data.length;
        }
    }
}


