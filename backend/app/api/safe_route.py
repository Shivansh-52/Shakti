"""
Safe Route API
Scores road segments and full routes using the trained RandomForest Regressor & Classifier models.
"""
import asyncio
import logging
from typing import List, Optional, Dict, Any

import httpx
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.ml.model_loader import predict_safety_score, predict_safety_classification

logger = logging.getLogger(__name__)
router = APIRouter()

# ---------------------------------------------------------------------------
# OSRM public instance — free routing
# ---------------------------------------------------------------------------
OSRM_BASE = "https://router.project-osrm.org/route/v1/foot"


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------

class LatLng(BaseModel):
    lat: float = Field(..., description="Latitude")
    lng: float = Field(..., description="Longitude")


class RouteRequest(BaseModel):
    origin: LatLng
    destination: LatLng
    is_night: bool = Field(False, description="Whether the journey is at night")


class SegmentSafety(BaseModel):
    lat: float
    lng: float
    safety_score: float          # 0–100 predicted continuous score
    safety_label: str            # Safe / Moderate / Unsafe
    safety_color: str            # Hex colour for map overlay


class RouteResponse(BaseModel):
    overall_safety_score: float  # 0–100 score
    overall_safety_label: str
    risk_classification: Optional[Dict[str, Any]] = None
    distance_km: float
    duration_min: float
    polyline: List[List[float]]  # [[lat, lng], ...]
    segments: List[SegmentSafety]
    tips: List[str]


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _safety_label(score: float) -> tuple[str, str]:
    """Return (label, hex_color) for a safety score (0–100 scale)."""
    if score >= 75.0:
        return "Safe", "#2E7D32"
    elif score >= 50.0:
        return "Moderate", "#F57C00"
    else:
        return "Unsafe", "#D32F2F"


def _build_tips(overall: float, is_night: bool) -> List[str]:
    tips = []
    if is_night:
        tips.append("🌙 Stay on well-lit main roads after dark in Dehradun.")
        tips.append("📍 Share your live tracking link with a trusted contact.")
    if overall < 50.0:
        tips.append("⚠️ This route has fewer active lighting checkpoints — consider staying on main arterial roads.")
        tips.append("🚔 Prefer routes with frequent police PCR patrol presence.")
    elif overall < 75.0:
        tips.append("🔦 Stay alert and stick to populated streets.")
    else:
        tips.append("✅ Highly recommended safe corridor with good lighting and emergency access.")
    tips.append("🆘 Shake your phone or say 'SOS' / 'Help' to activate emergency response anytime.")
    return tips


async def _fetch_osrm_route(origin: LatLng, dest: LatLng) -> dict:
    """Fetch walking route from OSRM."""
    coords = f"{origin.lng},{origin.lat};{dest.lng},{dest.lat}"
    url = f"{OSRM_BASE}/{coords}?overview=full&geometries=geojson&steps=false"
    async with httpx.AsyncClient(timeout=10.0) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        return resp.json()


def _score_segment(lat: float, lng: float, is_night: bool) -> SegmentSafety:
    """Score a single road segment coordinate using the trained Regressor."""
    features = {
        "origin_lat": lat,
        "origin_lon": lng,
        "is_night": 1 if is_night else 0,
        "lighting_score": 45.0 if is_night else 85.0,
        "visibility_score": 50.0 if is_night else 88.0,
        "crime_risk_est": 25.0 if is_night else 12.0,
    }

    raw_score = predict_safety_score(features)
    score = max(0.0, min(100.0, raw_score))
    label, color = _safety_label(score)
    return SegmentSafety(
        lat=lat,
        lng=lng,
        safety_score=round(score, 1),
        safety_label=label,
        safety_color=color,
    )


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@router.post("/score-route", response_model=RouteResponse)
async def score_route(req: RouteRequest):
    """
    Fetch a walking route from OSRM then score every segment with
    the trained RandomForest models (suraksha_setu_demo_regressor.joblib & suraksha_setu_demo_classifier.joblib).
    """
    try:
        osrm_data = await _fetch_osrm_route(req.origin, req.destination)
    except httpx.HTTPError as e:
        logger.warning(f"OSRM service error: {e}. Generating direct coordinate path.")
        osrm_data = {
            "routes": [{
                "distance": 3200.0,
                "duration": 1320.0,
                "geometry": {
                    "coordinates": [
                        [req.origin.lng, req.origin.lat],
                        [(req.origin.lng + req.destination.lng) / 2.0, (req.origin.lat + req.destination.lat) / 2.0],
                        [req.destination.lng, req.destination.lat],
                    ]
                }
            }]
        }

    routes = osrm_data.get("routes")
    if not routes:
        raise HTTPException(status_code=404, detail="No route found between the given points.")

    route = routes[0]
    distance_km = round(route["distance"] / 1000, 2)
    duration_min = round(route["duration"] / 60, 1)

    # Extract coordinate list [[lng, lat], ...] → convert to [[lat, lng]]
    coords_raw: List[List[float]] = route["geometry"]["coordinates"]
    polyline = [[c[1], c[0]] for c in coords_raw]

    # Sample route points for model evaluation
    SAMPLE_STEP = max(1, len(polyline) // 25)
    sampled = polyline[::SAMPLE_STEP]
    if polyline[-1] not in sampled:
        sampled.append(polyline[-1])

    # Score segments in executor thread
    loop = asyncio.get_event_loop()
    segments: List[SegmentSafety] = await loop.run_in_executor(
        None,
        lambda: [_score_segment(lat, lng, req.is_night) for lat, lng in sampled]
    )

    scores = [s.safety_score for s in segments]
    overall = round(sum(scores) / len(scores), 1) if scores else 80.0
    overall_label, _ = _safety_label(overall)
    tips = _build_tips(overall, req.is_night)

    # Overall route-level classification
    route_features = {
        "origin_lat": req.origin.lat,
        "origin_lon": req.origin.lng,
        "destination_lat": req.destination.lat,
        "destination_lon": req.destination.lng,
        "route_distance_km": distance_km,
        "route_distance_m": distance_km * 1000.0,
        "is_night": 1 if req.is_night else 0,
    }
    classification_result = await loop.run_in_executor(
        None,
        lambda: predict_safety_classification(route_features)
    )

    return RouteResponse(
        overall_safety_score=overall,
        overall_safety_label=overall_label,
        risk_classification=classification_result,
        distance_km=distance_km,
        duration_min=duration_min,
        polyline=polyline,
        segments=segments,
        tips=tips,
    )


@router.post("/score-point", response_model=SegmentSafety)
async def score_point(location: LatLng, is_night: bool = False):
    """Score the safety of a single geographic point."""
    loop = asyncio.get_event_loop()
    segment = await loop.run_in_executor(
        None,
        lambda: _score_segment(location.lat, location.lng, is_night)
    )
    return segment
