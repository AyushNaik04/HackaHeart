"""
demo_glucose.py
---------------
Interactive demo runner for glucose estimation functions.
Includes:
- SpO2-based estimation
- PPG/fuzzy estimation
- Calibration demo
"""

import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'pyproto'))

from glucose_estimators import estimate_glucose_from_spo2, estimate_glucose_fuzzy, calibrate_ppg_linear

def run_spo2_demo():
    print("=== SpO₂-Based Glucose Estimation ===")
    spo2_values = [95, 97, 99, 100]
    for spo2 in spo2_values:
        glucose = estimate_glucose_from_spo2(spo2)
        print(f"SpO₂ = {spo2}% → Estimated Glucose = {glucose} mg/dL")

def run_ppg_demo():
    print("\n=== PPG/Fuzzy-Based Glucose Estimation ===")
    ppg_values = [1.2, 1.5, 1.8, 2.0]
    fl_correction = 0.5
    for v_ppg in ppg_values:
        glucose = estimate_glucose_fuzzy(v_ppg, fl_correction=fl_correction)
        print(f"PPG Voltage = {v_ppg:.2f} V → Estimated Glucose = {glucose} mg/dL")

def run_calibration_demo():
    print("\n=== PPG Calibration Demo ===")
    v_list = [0.6, 0.7, 0.8, 0.9, 1.0]
    g_list = [110, 120, 130, 140, 150]
    a, b, r2 = calibrate_ppg_linear(v_list, g_list)
    print(f"Calibration results: a (slope) = {a:.2f}, b (intercept) = {b:.2f}, R² = {r2:.3f}")
    test_v = 0.85
    est_glucose = a * test_v + b
    print(f"Test PPG voltage = {test_v:.2f} → Estimated Glucose = {est_glucose:.2f} mg/dL")

def run_custom_input():
    print("\n=== Custom Input Demo ===")
    try:
        spo2 = float(input("Enter SpO₂ (%): "))
        v_ppg = float(input("Enter PPG voltage (V): "))
        glucose_spo2 = estimate_glucose_from_spo2(spo2)
        glucose_ppg = estimate_glucose_fuzzy(v_ppg)
        print(f"Estimated Glucose from SpO₂ = {glucose_spo2} mg/dL")
        print(f"Estimated Glucose from PPG = {glucose_ppg} mg/dL")
    except ValueError:
        print("Invalid input. Please enter numeric values.")

if __name__ == "__main__":
    run_spo2_demo()
    run_ppg_demo()
    run_calibration_demo()
