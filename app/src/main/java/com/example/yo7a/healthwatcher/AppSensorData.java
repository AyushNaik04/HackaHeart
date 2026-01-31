package com.example.yo7a.healthwatcher;

/**
 * Singleton class to store live sensor data (IR, Red, SpO2, Heart Rate)
 */
public class AppSensorData {

    private static AppSensorData instance;

    private double latestIR = 0.0;
    private double latestRed = 0.0;
    private double latestSpO2 = 0.0;
    private double latestHeartRate = 0.0;

    private AppSensorData() {}

    public static synchronized AppSensorData getInstance() {
        if (instance == null) {
            instance = new AppSensorData();
        }
        return instance;
    }

    // ✅ Update methods for live sensor values
    public synchronized void updateIR(double v) { this.latestIR = v; }
    public synchronized double getLatestIR() { return latestIR; }

    public synchronized void updateRed(double v) { this.latestRed = v; }
    public synchronized double getLatestRed() { return latestRed; }

    public synchronized void updateSpO2(double spo2) { this.latestSpO2 = spo2; }
    public synchronized double getLatestSpO2() { return latestSpO2; }

    public synchronized void updateHeartRate(double hr) { this.latestHeartRate = hr; }
    public synchronized double getLatestHeartRate() { return latestHeartRate; }
}
