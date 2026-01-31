package com.example.yo7a.healthwatcher.Math;

/**
 * Optimized FFT utility for HealthWatcher measurements.
 * Used for Heart Rate, Respiration, and Blood Pressure estimation.
 * Self-contained and numerically stable (no external libraries required).
 */
public class Fft {

    /**
     * Computes the dominant frequency from a time-series signal using FFT.
     *
     * @param signal        Input signal (Double[])
     * @param size          Number of samples (power of 2 recommended)
     * @param samplingFreq  Sampling frequency in Hz
     * @return Dominant frequency in Hz
     */
    public static double FFT(Double[] signal, int size, double samplingFreq) {
        // Step 1: Convert Double[] → double[]
        double[] x = new double[size];
        for (int i = 0; i < size; i++) {
            x[i] = (i < signal.length) ? signal[i] : 0.0;
        }

        // Step 2: Remove DC offset (center signal around zero)
        double mean = 0;
        for (double v : x) mean += v;
        mean /= size;
        for (int i = 0; i < size; i++) x[i] -= mean;

        // Step 3: Apply Hamming window to reduce spectral leakage
        for (int i = 0; i < size; i++) {
            x[i] *= 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (size - 1));
        }

        // Step 4: Initialize real and imaginary arrays
        double[] real = new double[size];
        double[] imag = new double[size];
        System.arraycopy(x, 0, real, 0, size);

        // Step 5: Perform FFT in place
        fftRadix2(real, imag);

        // Step 6: Compute magnitude spectrum
        double maxMag = 0;
        int maxIndex = 0;

        // Ignore low-frequency drift (<0.5 Hz)
        int minIndex = Math.max(1, (int) Math.ceil(size * 0.5 / samplingFreq));
        int maxSearch = size / 2; // Nyquist limit

        for (int i = minIndex; i < maxSearch; i++) {
            double mag = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
            if (mag > maxMag) {
                maxMag = mag;
                maxIndex = i;
            }
        }

        // Step 7: Calculate dominant frequency (in Hz)
        double dominantFreq = (maxIndex * samplingFreq) / size;

        // Step 8: Sanity check for valid physiological range (0.5 - 4.0 Hz)
        // 0.5 Hz ≈ 30 BPM, 4 Hz ≈ 240 BPM
        if (dominantFreq < 0.5 || dominantFreq > 4.0) {
            dominantFreq = 0; // reject noise
        }

        return dominantFreq;
    }

    /**
     * In-place Cooley–Tukey FFT (Radix-2).
     * Optimized for real-valued biomedical signals.
     *
     * @param real real part of input/output
     * @param imag imaginary part of input/output
     */
    private static void fftRadix2(double[] real, double[] imag) {
        int n = real.length;
        int levels = 31 - Integer.numberOfLeadingZeros(n);
        if ((1 << levels) != n) {
            throw new IllegalArgumentException("Signal length must be a power of 2");
        }

        // Bit-reversal permutation
        for (int i = 0; i < n; i++) {
            int j = Integer.reverse(i) >>> (32 - levels);
            if (j > i) {
                double tempReal = real[i];
                double tempImag = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tempReal;
                imag[j] = tempImag;
            }
        }

        // Cooley–Tukey decimation-in-time radix-2 FFT
        for (int size = 2; size <= n; size <<= 1) {
            int halfSize = size / 2;
            double phaseStep = -2 * Math.PI / size;

            for (int i = 0; i < n; i += size) {
                for (int j = 0; j < halfSize; j++) {
                    double angle = j * phaseStep;
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);

                    double tReal = cos * real[i + j + halfSize] - sin * imag[i + j + halfSize];
                    double tImag = sin * real[i + j + halfSize] + cos * imag[i + j + halfSize];

                    real[i + j + halfSize] = real[i + j] - tReal;
                    imag[i + j + halfSize] = imag[i + j] - tImag;
                    real[i + j] += tReal;
                    imag[i + j] += tImag;
                }
            }
        }
    }
}

