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


class AlternativeRoute(BaseModel):
    overall_safety_score: float
    overall_safety_label: str
    distance_km: float
    duration_min: float
    polyline: List[List[float]]
    segments: List[SegmentSafety]

class RouteResponse(BaseModel):
    overall_safety_score: float
    overall_safety_label: str
    risk_classification: Optional[Dict[str, Any]] = None
    distance_km: float
    duration_min: float
    polyline: List[List[float]]  # [[lat, lng], ...]
    segments: List[SegmentSafety]
    tips: List[str]
    alternatives: List[AlternativeRoute] = Field(default_factory=list)


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


async def _fetch_osrm_route(origin: LatLng, dest: LatLng, retries: int = 2) -> dict:
    """Fetch walking route from OSRM with retry and error handling."""
    coords = f"{origin.lng},{origin.lat};{dest.lng},{dest.lat}"
    url = f"{OSRM_BASE}/{coords}?overview=full&geometries=geojson&steps=false&alternatives=3"
    for attempt in range(1, retries + 1):
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(url)
                resp.raise_for_status()
                data = resp.json()
                if not data.get("routes"):
                    raise ValueError("OSRM returned empty routes")
                return data
        except (httpx.HTTPError, ValueError) as e:
            logger.warning(f"OSRM request failed (attempt {attempt}/{retries}): {e}")
            if attempt == retries:
                raise HTTPException(status_code=502, detail="Unable to fetch route from OSRM. Please try again later.")
            await asyncio.sleep(0.5)


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

    processed_routes = []
    loop = asyncio.get_event_loop()
    
    for route in routes:
        dist_km = round(route["distance"] / 1000, 2)
        dur_min = round(route["duration"] / 60, 1)
        
        coords_raw = route["geometry"]["coordinates"]
        polyline = [[c[1], c[0]] for c in coords_raw]
        
        SAMPLE_STEP = max(1, len(polyline) // 25)
        sampled = polyline[::SAMPLE_STEP]
        if polyline[-1] not in sampled:
            sampled.append(polyline[-1])
            
        segments = await loop.run_in_executor(
            None,
            lambda pts=sampled: [_score_segment(lat, lng, req.is_night) for lat, lng in pts]
        )
        
        scores = [s.safety_score for s in segments]
        overall = round(sum(scores) / len(scores), 1) if scores else 80.0
        overall_label, _ = _safety_label(overall)
        
        processed_routes.append({
            "distance_km": dist_km,
            "duration_min": dur_min,
            "polyline": polyline,
            "segments": segments,
            "overall_safety_score": overall,
            "overall_safety_label": overall_label
        })

    primary = processed_routes[0]
    tips = _build_tips(primary["overall_safety_score"], req.is_night)

    route_features = {
        "origin_lat": req.origin.lat,
        "origin_lon": req.origin.lng,
        "destination_lat": req.destination.lat,
        "destination_lon": req.destination.lng,
        "route_distance_km": primary["distance_km"],
        "route_distance_m": primary["distance_km"] * 1000.0,
        "is_night": 1 if req.is_night else 0,
    }
    classification_result = await loop.run_in_executor(
        None,
        lambda: predict_safety_classification(route_features)
    )
    
    alternatives = [AlternativeRoute(**alt) for alt in processed_routes[1:]]

    return RouteResponse(
        overall_safety_score=primary["overall_safety_score"],
        overall_safety_label=primary["overall_safety_label"],
        risk_classification=classification_result,
        distance_km=primary["distance_km"],
        duration_min=primary["duration_min"],
        polyline=primary["polyline"],
        segments=primary["segments"],
        tips=tips,
        alternatives=alternatives,
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
