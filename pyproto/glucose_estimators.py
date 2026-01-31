"""
glucose_estimators.py
---------------------
Prototype functions for estimating blood glucose from SpO₂ or PPG signals.
These are for testing in Python before porting into Java for the Android app.
"""

# -------------------------
# Imports
# -------------------------
import numpy as np
from sklearn.linear_model import LinearRegression

# -------------------------
# Glucose estimation functions
# -------------------------

def estimate_glucose_from_spo2(spo2: float) -> float:
    """
    Estimate blood glucose (mg/dL) from SpO₂ (%).
    Simple linear model.
    
    Args:
        spo2 (float): Peripheral oxygen saturation (percentage).
    
    Returns:
        float: Estimated glucose value in mg/dL.
    """
    glucose = 330 - 2.2 * spo2
    return round(glucose, 2)


def estimate_glucose_fuzzy(v_ppg: float, fl_correction: float = 0.0, 
                           a: float = 50.0, b: float = 50.0) -> float:
    """
    Estimate blood glucose (mg/dL) from IR/PPG voltage using fuzzy logic correction.
    
    Args:
        v_ppg (float): IR/PPG sensor voltage (normalized or raw).
        fl_correction (float): Correction term from fuzzy logic.
        a (float): Calibration slope constant.
        b (float): Calibration intercept constant.
    
    Returns:
        float: Estimated glucose value in mg/dL.
    """
    glucose = a * v_ppg + b + fl_correction
    return round(glucose, 2)


def calibrate_ppg_linear(v_ppg_list, glucose_list):
    """
    Fit a linear regression model:
        glucose = a * v_ppg + b
    Returns:
        a (float): slope
        b (float): intercept
        r2 (float): goodness of fit
    """
    # Convert lists to numpy arrays
    X = np.array(v_ppg_list).reshape(-1, 1)
    y = np.array(glucose_list)

    # Fit linear regression
    lr = LinearRegression().fit(X, y)

    # Extract slope, intercept, and R²
    a = float(lr.coef_[0])
    b = float(lr.intercept_)
    r2 = float(lr.score(X, y))

    return a, b, r2
