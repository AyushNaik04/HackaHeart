package com.example.yo7a.healthwatcher;

import java.util.LinkedList;
import java.util.Queue;

/**
 * ImageProcessing
 *
 * Optimized utilities for extracting RGB data and stabilizing PPG signals from
 * YUV420SP (NV21) frames in real-time.
 *
 * This version includes:
 *  - Rolling average smoothing
 *  - Exposure normalization
 *  - Invalid frame rejection
 */
public final class ImageProcessing {

    private ImageProcessing() { }

    private static final int SCALE = 256;
    private static final int C_R_V = 409;
    private static final int C_G_V = 208;
    private static final int C_G_U = 100;
    private static final int C_B_U = 517;
    private static final int C_Y = 298;

    private static final int Y_OFFSET = 16;
    private static final int UV_OFFSET = 128;
    private static final int MIN_CLAMP = 0;
    private static final int MAX_CLAMP = 255;

    private static final int SMOOTHING_WINDOW = 8;
    private static final Queue<Double> redAvgHistory = new LinkedList<>();

    private static int clampToByte(int v) {
        if (v < MIN_CLAMP) return MIN_CLAMP;
        if (v > MAX_CLAMP) return MAX_CLAMP;
        return v;
    }

    /**
     * Decode YUV420SP frame to red/blue/green average intensity with stabilization.
     *
     * @param yuv420sp camera preview frame
     * @param width frame width
     * @param height frame height
     * @param type 1=Red, 2=Blue, 3=Green
     * @return stabilized average channel intensity (0–255)
     */
    public static double decodeYUV420SPtoRedBlueGreenAvg(byte[] yuv420sp, int width, int height, int type) {
        if (yuv420sp == null || width <= 0 || height <= 0) return 0.0;

        final int frameSize = width * height;
        long sumR = 0, sumG = 0, sumB = 0;
        final int uvStart = frameSize;
        int yp = 0;

        for (int j = 0; j < height; j++) {
            int uvp = uvStart + (j >> 1) * width;
            int v = 0, u = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (yuv420sp[yp] & 0xff) - Y_OFFSET;
                if (y < 0) y = 0;

                if ((i & 1) == 0) {
                    v = (yuv420sp[uvp++] & 0xff) - UV_OFFSET;
                    u = (yuv420sp[uvp++] & 0xff) - UV_OFFSET;
                }

                int yScaled = C_Y * y;
                int r = (yScaled + C_R_V * v + (SCALE >> 1)) >> 8;
                int g = (yScaled - C_G_V * v - C_G_U * u + (SCALE >> 1)) >> 8;
                int b = (yScaled + C_B_U * u + (SCALE >> 1)) >> 8;

                sumR += clampToByte(r);
                sumG += clampToByte(g);
                sumB += clampToByte(b);
            }
        }

        double avgR = ((double) sumR) / frameSize;
        double avgG = ((double) sumG) / frameSize;
        double avgB = ((double) sumB) / frameSize;

        // Exposure normalization (reduce brightness shifts)
        double luminance = (avgR + avgG + avgB) / 3.0;
        if (luminance > 0) {
            avgR = (avgR / luminance) * 128.0;
        }

        // Smoothing (rolling average)
        double smoothedRed = smoothValue(avgR);

        // Reject invalid readings (no finger)
        if (smoothedRed < 30 || smoothedRed > 230) return 0.0;

        switch (type) {
            case 1: return smoothedRed;
            case 2: return avgB;
            case 3: return avgG;
            default: return 0.0;
        }
    }

    /** Rolling average smoothing for red signal */
    private static double smoothValue(double newValue) {
        if (redAvgHistory.size() >= SMOOTHING_WINDOW)
            redAvgHistory.poll();
        redAvgHistory.offer(newValue);

        double sum = 0;
        for (double val : redAvgHistory) sum += val;
        return sum / redAvgHistory.size();
    }

    /** Optional: Remove DC component (for FFT preprocessing) */
    public static void removeDC(double[] samples) {
        if (samples == null || samples.length == 0) return;
        double sum = 0;
        for (double s : samples) sum += s;
        double mean = sum / samples.length;
        for (int i = 0; i < samples.length; i++) samples[i] -= mean;
    }

    /** Optional: Normalize unsigned byte channel buffer to [-1,1] */
    public static void normalizeChannelToDouble(byte[] inBytes, double[] outDoubles) {
        if (inBytes == null || outDoubles == null) return;
        int n = Math.min(inBytes.length, outDoubles.length);
        for (int i = 0; i < n; i++) outDoubles[i] = ((inBytes[i] & 0xff) / 127.5) - 1.0;
    }
}
