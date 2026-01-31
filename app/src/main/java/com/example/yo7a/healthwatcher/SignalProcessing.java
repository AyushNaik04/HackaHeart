package com.example.yo7a.healthwatcher;

public final class SignalProcessing {

    private SignalProcessing() {}

    /**
     * Remove linear trend from samples (in-place).
     * Uses simple least-squares slope removal (fast and stable).
     */
    public static void removeLinearTrend(double[] x) {
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
        for (int i = 0; i < n; i++) {
            x[i] = x[i] - (slope * i + intercept);
        }
    }

    /**
     * Apply Hamming window (in place).
     */
    public static void applyHammingWindow(double[] x) {
        if (x == null) return;
        int n = x.length;
        for (int i = 0; i < n; i++) {
            x[i] *= 0.54 - 0.46 * Math.cos((2.0 * Math.PI * i) / (n - 1));
        }
    }

    /**
     * Compute next power-of-two >= n
     */
    public static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    /**
     * Compute magnitude spectrum from real & imag arrays (in place: returns mags array).
     * Only first n/2 bins are meaningful for real input.
     */
    public static double[] magnitudeSpectrum(double[] real, double[] imag) {
        int n = real.length;
        int half = n / 2;
        double[] mags = new double[half];
        for (int i = 0; i < half; i++) {
            mags[i] = Math.hypot(real[i], imag[i]);
        }
        return mags;
    }

    /**
     * Quadratic (parabolic) interpolation around index k to refine peak frequency.
     * Requires mags array and integer index k where mags[k] is peak (not at edges).
     * Returns sub-bin offset in bins (float between -0.5..0.5 typically).
     */
    public static double quadraticInterp(double[] mags, int k) {
        if (mags == null || k <= 0 || k >= mags.length - 1) return 0.0;
        double alpha = mags[k - 1];
        double beta = mags[k];
        double gamma = mags[k + 1];
        double denom = (alpha - 2.0 * beta + gamma);
        if (Math.abs(denom) < 1e-12) return 0.0;
        double shift = 0.5 * (alpha - gamma) / denom;
        return shift; // in bins
    }

    /**
     * Compute SNR estimate: ratio of peak energy to median of spectrum (or mean)
     */
    public static double computeSNR(double[] mags, int peakIndex, int skipBins) {
        if (mags == null || mags.length == 0) return 0.0;
        double peak = mags[peakIndex];
        double sum = 0.0;
        int count = 0;
        for (int i = skipBins; i < mags.length; i++) {
            if (i >= peakIndex - 2 && i <= peakIndex + 2) continue;
            sum += mags[i];
            count++;
        }
        double noise = (count > 0) ? (sum / count) : 1e-12;
        if (noise <= 0) noise = 1e-12;
        return peak / noise;
    }

    /**
     * Find dominant frequency in Hz from a time-domain real signal:
     * - detrend, window, zero-pad to pow2, FFT (uses Fft.fftRadix2 via Fft.FFT helper),
     * - compute magnitude, find peak in physiological band, refine peak with quadratic interp,
     * - return frequency in Hz and SNR via the outSNR[0] parameter (optional).
     *
     * Parameters:
     * - samples: input signal (double[]) length = N samples
     * - samplingFreqHz: sampling frequency in Hz
     * - minHz, maxHz: search band in Hz (e.g., for HR 0.7..4.0 Hz -> 42..240 BPM)
     * - outSNR: if non-null array of length>=1 will receive SNR estimate
     *
     * Note: this uses your project's Fft.FFT(...) for the lower-level FFT if you want.
     *       We will copy samples into a power-of-two buffer, call the Fft.fftRadix2 internals
     *       (we expose a simple variant below for compatibility).
     */
    public static double findDominantFrequencyHz(double[] samples, double samplingFreqHz,
                                                 double minHz, double maxHz, double[] outSNR) {
        if (samples == null || samples.length < 4) return Double.NaN;
        // copy and detrend
        double[] x = samples.clone();
        removeLinearTrend(x);
        applyHammingWindow(x);

        int n = x.length;
        int fftSize = nextPowerOfTwo(n);
        double[] real = new double[fftSize];
        double[] imag = new double[fftSize];
        System.arraycopy(x, 0, real, 0, n);
        for (int i = n; i < fftSize; i++) real[i] = 0.0;
        for (int i = 0; i < fftSize; i++) imag[i] = 0.0;

        // run in-place radix-2 FFT from your Fft class (we expose a static method there)
        // NOTE: Fft.fftRadix2 is private in your Fft — but your Fft has a public FFT(...) that performs windowing etc.
        // To avoid duplication, we will call your Fft.FFT(Double[], size, samplingFreq) if available.
        // Here, to be robust, we'll compute spectrum using a simple method: call Fft.FFT to get dominant freq.
        // But we also need mags for SNR/peak interpolation. So we'll re-use your Fft.fftRadix2 if you expose it,
        // otherwise call your public FFT to get coarse frequency. To keep this helper self-contained I'll implement
        // a small bit-reversed FFT here using the same algorithm as Fft.fftRadix2 if necessary.
        // For portability, we'll call FftInternal.fftRadix2(real, imag) below (FftInternal is a tiny helper class).
        FftInternal.fftRadix2(real, imag);

        double[] mags = new double[fftSize / 2];
        for (int i = 0; i < mags.length; i++) mags[i] = Math.hypot(real[i], imag[i]);

        // frequency bin resolution
        double freqRes = samplingFreqHz / fftSize;

        // convert search band to bins
        int minBin = Math.max(1, (int) Math.floor(minHz / freqRes));
        int maxBin = Math.min(mags.length - 1, (int) Math.ceil(maxHz / freqRes));

        // find peak within band
        double maxMag = 0.0;
        int maxIdx = minBin;
        for (int i = minBin; i <= maxBin; i++) {
            if (mags[i] > maxMag) {
                maxMag = mags[i];
                maxIdx = i;
            }
        }

        // compute sub-bin interpolation
        double shift = quadraticInterp(mags, maxIdx);
        double peakBin = maxIdx + shift;
        double freqHz = peakBin * freqRes;

        // SNR estimate
        if (outSNR != null && outSNR.length > 0) {
            outSNR[0] = computeSNR(mags, maxIdx, 2);
        }

        return freqHz;
    }
}

/**
 * Minimal internal FFT helper: public since SignalProcessing needs it.
 * This is the same canonical radix-2 FFT implementation used elsewhere.
 * If you already have Fft.fftRadix2 accessible, you can remove this class and call that one.
 */
final class FftInternal {
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
