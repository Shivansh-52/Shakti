"""
Safe Route Model Loader
Loads and manages the trained RandomForest models (Regressor & Classifier) for road safety scoring.
"""
import os
import pickle
import warnings
import logging
import datetime
from pathlib import Path
from typing import Dict, Any, Tuple, Optional

logger = logging.getLogger(__name__)

# Suppress sklearn version mismatch warnings gracefully
warnings.filterwarnings("ignore", category=UserWarning, module="sklearn")

BACKEND_DIR = Path(__file__).parent.parent.parent
REGRESSOR_PATH = BACKEND_DIR / "suraksha_setu_demo_regressor.joblib"
CLASSIFIER_PATH = BACKEND_DIR / "suraksha_setu_demo_classifier.joblib"
LEGACY_MODEL_PATH = BACKEND_DIR / "surakshasetu_safe_route_model.pkl"

# Features for the demo regressor & classifier
DEMO_FEATURES = [
    "route_number",
    "origin_lat",
    "origin_lon",
    "destination_lat",
    "destination_lon",
    "route_distance_m",
    "route_distance_km",
    "route_node_count",
    "route_edge_count",
    "road_type_count",
    "residential_segments",
    "primary_segments",
    "secondary_segments",
    "tertiary_segments",
    "service_segments",
    "oneway_ratio",
    "is_weekend",
    "hour",
    "is_night",
    "weather_penalty",
    "lighting_score",
    "effective_lighting",
    "cctv_coverage_est",
    "pedestrian_activity_est",
    "public_presence_score",
    "traffic_level_est",
    "crime_risk_est",
    "emergency_access_score",
    "visibility_score",
    "estimated_speed_kmh",
    "estimated_travel_time_min",
    "distance_per_edge",
    "nodes_per_km",
    "major_road_ratio",
    "service_road_ratio",
    "lighting_cctv_score",
    "visibility_public_score",
    "emergency_public_score",
    "night_crime_interaction",
    "weather_visibility_interaction",
    "day_of_week_Friday",
    "day_of_week_Monday",
    "day_of_week_Saturday",
    "day_of_week_Sunday",
    "day_of_week_Thursday",
    "day_of_week_Tuesday",
    "day_of_week_Wednesday",
    "weather_condition_Clear",
    "weather_condition_Cloudy",
    "weather_condition_Fog",
    "weather_condition_Rain",
]

_regressor_model = None
_classifier_model = None
_legacy_model = None


def _inject_sklearn_compat():
    """
    Inject compatibility shims if needed for older pickle/joblib files across sklearn versions.
    """
    warnings.filterwarnings("ignore")
    try:
        import sklearn.compose._column_transformer as _ct
        if not hasattr(_ct, "_RemainderColsList"):
            class _RemainderColsList(list):
                def __init__(self, data=None, future_dtype=None, warning_was_emitted=False, warning_enabled=True):
                    super().__init__(data or [])
                    self.future_dtype = future_dtype
                    self.warning_was_emitted = warning_was_emitted
                    self.warning_enabled = warning_enabled
            _ct._RemainderColsList = _RemainderColsList
    except Exception as exc:
        pass


def load_models():
    """Load both Regressor and Classifier models."""
    global _regressor_model, _classifier_model, _legacy_model
    _inject_sklearn_compat()
    import joblib

    # 1. Load Regressor
    if _regressor_model is None and REGRESSOR_PATH.exists():
        try:
            logger.info(f"Loading Regressor model from {REGRESSOR_PATH.name}...")
            _regressor_model = joblib.load(str(REGRESSOR_PATH))
            logger.info("Regressor loaded successfully.")
        except Exception as e:
            logger.error(f"Failed loading {REGRESSOR_PATH}: {e}")

    # 2. Load Classifier
    if _classifier_model is None and CLASSIFIER_PATH.exists():
        try:
            logger.info(f"Loading Classifier model from {CLASSIFIER_PATH.name}...")
            _classifier_model = joblib.load(str(CLASSIFIER_PATH))
            logger.info("Classifier loaded successfully.")
        except Exception as e:
            logger.error(f"Failed loading {CLASSIFIER_PATH}: {e}")

    # 3. Fallback Legacy Model if needed
    if _regressor_model is None and LEGACY_MODEL_PATH.exists():
        try:
            _legacy_model = joblib.load(str(LEGACY_MODEL_PATH))
        except Exception:
            with open(LEGACY_MODEL_PATH, "rb") as f:
                _legacy_model = pickle.load(f)

    return _regressor_model, _classifier_model


def get_regressor():
    global _regressor_model
    if _regressor_model is None:
        load_models()
    return _regressor_model


def get_classifier():
    global _classifier_model
    if _classifier_model is None:
        load_models()
    return _classifier_model


def build_feature_dataframe(features: Dict[str, Any]):
    """
    Constructs a standard single-row pandas DataFrame matching DEMO_FEATURES.
    """
    import pandas as pd
    now = datetime.datetime.now()
    hour = features.get("hour", now.hour)
    is_night = features.get("is_night", 1 if (hour >= 20 or hour <= 6) else 0)
    is_weekend = features.get("is_weekend", 1 if now.weekday() >= 5 else 0)

    origin_lat = features.get("origin_lat", features.get("latitude_road", 30.3435))
    origin_lon = features.get("origin_lon", features.get("longitude_road", 77.8867))
    dest_lat = features.get("destination_lat", origin_lat + 0.01)
    dest_lon = features.get("destination_lon", origin_lon + 0.01)

    dist_km = features.get("route_distance_km", 2.5)
    dist_m = features.get("route_distance_m", dist_km * 1000.0)

    lighting = features.get("lighting_score", 40.0 if is_night else 80.0)
    cctv = features.get("cctv_coverage_est", 65.0)
    public_pres = features.get("public_presence_score", 45.0 if is_night else 75.0)
    crime_risk = features.get("crime_risk_est", 25.0 if is_night else 15.0)
    emerg_access = features.get("emergency_access_score", 80.0)
    visibility = features.get("visibility_score", 50.0 if is_night else 85.0)

    row = {f: 0.0 for f in DEMO_FEATURES}
    row.update({
        "route_number": features.get("route_number", 1),
        "origin_lat": origin_lat,
        "origin_lon": origin_lon,
        "destination_lat": dest_lat,
        "destination_lon": dest_lon,
        "route_distance_m": dist_m,
        "route_distance_km": dist_km,
        "route_node_count": features.get("route_node_count", 30),
        "route_edge_count": features.get("route_edge_count", 29),
        "road_type_count": features.get("road_type_count", 3),
        "residential_segments": features.get("residential_segments", 8),
        "primary_segments": features.get("primary_segments", 15),
        "secondary_segments": features.get("secondary_segments", 6),
        "tertiary_segments": features.get("tertiary_segments", 0),
        "service_segments": features.get("service_segments", 0),
        "oneway_ratio": features.get("oneway_ratio", 0.1),
        "is_weekend": is_weekend,
        "hour": hour,
        "is_night": is_night,
        "weather_penalty": features.get("weather_penalty", 0.0),
        "lighting_score": lighting,
        "effective_lighting": features.get("effective_lighting", lighting * (0.8 if is_night else 1.0)),
        "cctv_coverage_est": cctv,
        "pedestrian_activity_est": features.get("pedestrian_activity_est", 35.0 if is_night else 65.0),
        "public_presence_score": public_pres,
        "traffic_level_est": features.get("traffic_level_est", 30.0 if is_night else 60.0),
        "crime_risk_est": crime_risk,
        "emergency_access_score": emerg_access,
        "visibility_score": visibility,
        "estimated_speed_kmh": features.get("estimated_speed_kmh", 25.0),
        "estimated_travel_time_min": features.get("estimated_travel_time_min", 15.0),
        "distance_per_edge": dist_m / max(1, features.get("route_edge_count", 29)),
        "nodes_per_km": features.get("route_node_count", 30) / max(0.1, dist_km),
        "major_road_ratio": features.get("major_road_ratio", 0.7),
        "service_road_ratio": features.get("service_road_ratio", 0.0),
        "lighting_cctv_score": (lighting + cctv) / 2.0,
        "visibility_public_score": (visibility + public_pres) / 2.0,
        "emergency_public_score": (emerg_access + public_pres) / 2.0,
        "night_crime_interaction": crime_risk * is_night,
        "weather_visibility_interaction": visibility,
        f"day_of_week_{now.strftime('%A')}": 1.0,
        "weather_condition_Clear": 1.0,
    })

    return pd.DataFrame([row], columns=DEMO_FEATURES)


def predict_safety_score(features: dict) -> float:
    """
    Given a feature dict, predicts a continuous safety score (0–100 scale).
    """
    try:
        reg = get_regressor()
        if reg is not None:
            df = build_feature_dataframe(features)
            score = float(reg.predict(df)[0])
            # Regressor predicts in 0–100 range
            return max(0.0, min(100.0, score))
    except Exception as e:
        logger.warning(f"Error in regressor prediction: {e}")

    # Fallback heuristic if model is unavailable
    is_night = features.get("is_night", 0)
    base_score = 65.0 if is_night else 85.0
    return base_score


def predict_safety_classification(features: dict) -> Dict[str, Any]:
    """
    Given a feature dict, predicts safety classification (class 0: Safe, 1: Moderate, 2: Risky) and probabilities.
    """
    try:
        clf = get_classifier()
        if clf is not None:
            df = build_feature_dataframe(features)
            pred_class = int(clf.predict(df)[0])
            pred_proba = [float(p) for p in clf.predict_proba(df)[0]]
            class_labels = {0: "Safe", 1: "Moderate", 2: "High Risk"}
            return {
                "class_id": pred_class,
                "label": class_labels.get(pred_class, "Moderate"),
                "probabilities": {
                    "safe": pred_proba[0] if len(pred_proba) > 0 else 0.0,
                    "moderate": pred_proba[1] if len(pred_proba) > 1 else 0.0,
                    "high_risk": pred_proba[2] if len(pred_proba) > 2 else 0.0,
                },
            }
    except Exception as e:
        logger.warning(f"Error in classifier prediction: {e}")

    return {
        "class_id": 0,
        "label": "Safe",
        "probabilities": {"safe": 0.85, "moderate": 0.12, "high_risk": 0.03},
    }
