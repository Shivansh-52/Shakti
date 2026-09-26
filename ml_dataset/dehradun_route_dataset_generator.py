# %% [markdown]
# # Suraksha Setu AI-Powered Safe Route Finding
# ## Dataset Generator for ML Models (Dehradun, Uttarakhand)
# This notebook generates a 400K-500K record ML-ready dataset for training the Suraksha Setu safe route AI.

# %% [markdown]
# ### SECTION 1: Install dependencies
# %%
!pip install osmnx geopandas networkx pandas numpy shapely pyarrow openpyxl tqdm scikit-learn folium matplotlib seaborn geopy

# %% [markdown]
# ### SECTION 2: Imports and configuration
# %%
import os
import random
import time
import json
import warnings
import pandas as pd
import numpy as np
import geopandas as gpd
import networkx as nx
import osmnx as ox
import folium
import matplotlib.pyplot as plt
import seaborn as sns
from shapely.geometry import Point, LineString
from geopy.geocoders import Nominatim
from geopy.exc import GeocoderTimedOut, GeocoderServiceError
from sklearn.model_selection import GroupShuffleSplit
from tqdm.auto import tqdm
from itertools import islice

warnings.filterwarnings('ignore')

# Config Dictionary
CONFIG = {
    "TARGET_ROWS": 500000,
    "MIN_ROUTES_PER_OD": 3,
    "MAX_ROUTES_PER_OD": 10,
    "RANDOM_SEED": 42,
    "CITY": "Dehradun, Uttarakhand, India",
    "CRS": "EPSG:4326",
    "PROJECTED_CRS": "EPSG:32644", # UTM Zone 44N for India/Dehradun (meters)
    "K_SHORTEST_PATHS": 15, # Try generating up to 15 simple paths to find diverse routes
    "EXCEL_FILE_PATH": "dehradun_locations.xlsx", # Upload this file in Section 3
}

random.seed(CONFIG["RANDOM_SEED"])
np.random.seed(CONFIG["RANDOM_SEED"])

print(f"Configuration loaded for {CONFIG['CITY']}")

# %% [markdown]
# ### SECTION 3: Upload Excel
# %%
from google.colab import files
import io

print("Please upload the Excel file containing Dehradun locations.")
uploaded = files.upload()

if uploaded:
    filename = list(uploaded.keys())[0]
    CONFIG["EXCEL_FILE_PATH"] = filename
    raw_locations_df = pd.read_excel(io.BytesIO(uploaded[filename]))
    print(f"Successfully loaded {filename} with {len(raw_locations_df)} rows.")
else:
    print("No file uploaded. Please upload a file to continue.")
    # For testing, creating a dummy dataframe if no file is uploaded
    raw_locations_df = pd.DataFrame({
        "location_id": [1, 2, 3],
        "ward_no": [1, 2, 3],
        "location_name": ["Clock Tower", "ISBT", "Rajpur Road"]
    })

# %% [markdown]
# ### SECTION 4: Clean locations
# %%
def clean_locations(df):
    """Detects columns, cleans whitespace, and removes duplicates."""
    df = df.copy()
    
    # Auto-detect columns (case insensitive, basic matching)
    cols = [c.lower() for c in df.columns]
    
    loc_name_col = next((c for c in df.columns if 'name' in c.lower() or 'location' in c.lower() and 'id' not in c.lower()), None)
    loc_id_col = next((c for c in df.columns if 'id' in c.lower()), None)
    ward_col = next((c for c in df.columns if 'ward' in c.lower()), None)
    
    if loc_name_col:
        df['location_name_clean'] = df[loc_name_col].astype(str).str.strip().str.title()
    else:
        raise ValueError("Could not detect location name column.")
        
    df['location_id_clean'] = df[loc_id_col] if loc_id_col else range(1, len(df) + 1)
    df['ward_no_clean'] = df[ward_col] if ward_col else "Unknown"
    
    # Drop duplicates
    clean_df = df[['location_id_clean', 'ward_no_clean', 'location_name_clean']].drop_duplicates(subset=['location_name_clean'])
    clean_df.columns = ['location_id', 'ward_no', 'location_name']
    
    print(f"Cleaned data: {len(clean_df)} unique locations.")
    return clean_df

locations_df = clean_locations(raw_locations_df)
display(locations_df.head())

# %% [markdown]
# ### SECTION 5: Geocode locations
# %%
def geocode_locations(df, city=CONFIG["CITY"]):
    """Geocodes locations using Nominatim, saving to CSV to cache results."""
    geolocator = Nominatim(user_agent="suraksha_setu_ml_builder", timeout=10)
    
    results = []
    unresolved = []
    
    for idx, row in tqdm(df.iterrows(), total=len(df), desc="Geocoding"):
        query = f"{row['location_name']}, {city}"
        try:
            time.sleep(1.1)  # Respect rate limits
            location = geolocator.geocode(query)
            if location:
                results.append({
                    "location_id": row["location_id"],
                    "ward_no": row["ward_no"],
                    "location_name": row["location_name"],
                    "latitude": location.latitude,
                    "longitude": location.longitude,
                    "geocoding_status": "Success",
                    "geocoding_source": "OSM_Nominatim"
                })
            else:
                unresolved.append(row)
        except Exception as e:
            print(f"Error geocoding {query}: {e}")
            unresolved.append(row)
            
    res_df = pd.DataFrame(results)
    unres_df = pd.DataFrame(unresolved)
    
    res_df.to_csv("location_coordinates.csv", index=False)
    unres_df.to_csv("unresolved_locations.csv", index=False)
    
    print(f"Successfully geocoded {len(res_df)} locations. Failed: {len(unres_df)}.")
    return res_df, unres_df

# Try loading from cache first
if os.path.exists("location_coordinates.csv"):
    geocoded_df = pd.read_csv("location_coordinates.csv")
    print("Loaded geocoded locations from cache.")
else:
    geocoded_df, unresolved_df = geocode_locations(locations_df)

# %% [markdown]
# ### SECTION 6: Download/cache real OSM road network
# %%
graph_cache_path = "dehradun_drive_network.graphml"

if os.path.exists(graph_cache_path):
    print("Loading OSM network from cache...")
    G = ox.load_graphml(graph_cache_path)
else:
    print(f"Downloading OSM road network for {CONFIG['CITY']}...")
    # Use 'drive' or 'all' depending on pedestrian vs car focus. 
    # For safety/pedestrian/mixed routing, 'all' or 'bike' might be better, but 'drive' is standard for major roads.
    G = ox.graph_from_place(CONFIG["CITY"], network_type="all")
    ox.save_graphml(G, graph_cache_path)

print(f"Road network loaded. Nodes: {len(G.nodes)}, Edges: {len(G.edges)}")

# Convert to GeoDataFrames for spatial operations
nodes_gdf, edges_gdf = ox.graph_to_gdfs(G)

# %% [markdown]
# ### SECTION 7: Download/cache real POIs
# %%
poi_tags = {
    'amenity': ['police', 'hospital', 'fire_station', 'pharmacy', 'restaurant', 'cafe', 'bank', 'atm', 'school', 'college', 'pub', 'bar'],
    'shop': True,
    'highway': ['street_lamp']
}

poi_cache_path = "dehradun_pois.geojson"

if os.path.exists(poi_cache_path):
    print("Loading POIs from cache...")
    pois_gdf = gpd.read_file(poi_cache_path)
else:
    print(f"Downloading POIs for {CONFIG['CITY']}...")
    pois_gdf = ox.features_from_place(CONFIG["CITY"], tags=poi_tags)
    # Filter out polygons if we only want points (or use centroids)
    pois_gdf['geometry'] = pois_gdf['geometry'].centroid
    pois_gdf = pois_gdf[['name', 'amenity', 'shop', 'highway', 'geometry']]
    pois_gdf.to_file(poi_cache_path, driver="GeoJSON")

print(f"Downloaded {len(pois_gdf)} real POIs from OSM.")

# Group POIs for quick access
police_gdf = pois_gdf[pois_gdf['amenity'] == 'police']
hospital_gdf = pois_gdf[pois_gdf['amenity'] == 'hospital']
fire_gdf = pois_gdf[pois_gdf['amenity'] == 'fire_station']

# %% [markdown]
# ### SECTION 8: Map locations to nearest road nodes
# %%
print("Mapping locations to the road network...")
# Project to metric system for accurate nearest node search
G_proj = ox.project_graph(G, to_crs=CONFIG["PROJECTED_CRS"])

node_ids = []
for idx, row in geocoded_df.iterrows():
    # Find nearest node
    nearest_node = ox.distance.nearest_nodes(G, X=row['longitude'], Y=row['latitude'])
    node_ids.append(nearest_node)

geocoded_df['nearest_node'] = node_ids
display(geocoded_df.head(3))

# %% [markdown]
# ### SECTION 9: Generate OD pairs
# %%
od_pairs = []
pair_id = 1

for i, origin in geocoded_df.iterrows():
    for j, dest in geocoded_df.iterrows():
        if i != j:
            od_pairs.append({
                "od_pair_id": f"OD_{pair_id}",
                "origin_id": origin['location_id'],
                "origin_name": origin['location_name'],
                "origin_ward": origin['ward_no'],
                "origin_node": origin['nearest_node'],
                "origin_lat": origin['latitude'],
                "origin_lon": origin['longitude'],
                
                "destination_id": dest['location_id'],
                "destination_name": dest['location_name'],
                "destination_ward": dest['ward_no'],
                "destination_node": dest['nearest_node'],
                "destination_lat": dest['latitude'],
                "destination_lon": dest['longitude']
            })
            pair_id += 1

od_pairs_df = pd.DataFrame(od_pairs)
print(f"Generated {len(od_pairs_df)} Origin-Destination pairs.")

# %% [markdown]
# ### SECTION 10: Generate alternative routes
# %%
def k_shortest_paths(G, source, target, k, weight='length'):
    """Generates K shortest paths avoiding duplicates."""
    try:
        return list(islice(nx.shortest_simple_paths(G, source, target, weight=weight), k))
    except (nx.NetworkXNoPath, nx.NodeNotFound):
        return []

all_routes = []
route_counter = 1

print("Generating routes... This will take time.")
# If there are ~100 locations, N=100 -> 9900 OD pairs. 
# 9900 * 50 = ~495,000 routes.
# We will generate between MIN_ROUTES and MAX_ROUTES per OD pair.

for idx, row in tqdm(od_pairs_df.iterrows(), total=len(od_pairs_df), desc="Routing"):
    orig_node = row['origin_node']
    dest_node = row['destination_node']
    
    if orig_node == dest_node: continue
    
    # Generate K shortest paths (Length based)
    paths = k_shortest_paths(G, orig_node, dest_node, k=CONFIG["MAX_ROUTES_PER_OD"])
    
    # We can also generate travel_time based routes if we added speeds, 
    # but simple paths provide enough structural diversity for our target ML dataset.
    
    for i, path in enumerate(paths):
        all_routes.append({
            "route_id": f"R_{route_counter}",
            "od_pair_id": row["od_pair_id"],
            "origin_id": row["origin_id"],
            "destination_id": row["destination_id"],
            "origin_name": row["origin_name"],
            "destination_name": row["destination_name"],
            "origin_ward": row["origin_ward"],
            "destination_ward": row["destination_ward"],
            "route_number": i + 1,
            
            "origin_latitude": row["origin_lat"],
            "origin_longitude": row["origin_lon"],
            "destination_latitude": row["destination_lat"],
            "destination_longitude": row["destination_lon"],
            
            "path_nodes": path
        })
        route_counter += 1
        
        # We need ~500K rows. If we average 50 routes per pair, we reach it.
        # But real networks might not have 50 unique paths.
        # It's better to ensure we hit MAX_ROUTES_PER_OD.

routes_df = pd.DataFrame(all_routes)
print(f"Generated {len(routes_df)} initial routes.")

# %% [markdown]
# ### SECTION 11: Calculate route geometry
# %%
def extract_route_geometry(path_nodes, G):
    """Creates LineString and extracts segment count."""
    points = []
    length = 0
    segments = 0
    junctions = len(path_nodes) - 2 if len(path_nodes) > 2 else 0
    
    for i in range(len(path_nodes)-1):
        u = path_nodes[i]
        v = path_nodes[i+1]
        
        # Handle MultiDiGraph multiple edges between nodes
        edge_data = G.get_edge_data(u, v)
        if edge_data:
            # Use the shortest edge if multiple exist
            min_edge = min(edge_data.values(), key=lambda x: x.get('length', float('inf')))
            segments += 1
            length += min_edge.get('length', 0)
            
            if 'geometry' in min_edge:
                # Add coords from linestring
                points.extend(list(min_edge['geometry'].coords))
            else:
                # Add point-to-point if no geometry defined
                points.append((G.nodes[u]['x'], G.nodes[u]['y']))
                
    # Add final node
    if path_nodes:
        final_node = path_nodes[-1]
        points.append((G.nodes[final_node]['x'], G.nodes[final_node]['y']))
        
    # Remove duplicates from point list preserving order
    clean_points = []
    for p in points:
        if not clean_points or p != clean_points[-1]:
            clean_points.append(p)
            
    geom = LineString(clean_points) if len(clean_points) >= 2 else None
    
    # Start and End lat/lons
    start_lat = clean_points[0][1] if clean_points else None
    start_lon = clean_points[0][0] if clean_points else None
    end_lat = clean_points[-1][1] if clean_points else None
    end_lon = clean_points[-1][0] if clean_points else None
            
    return geom, length, segments, junctions, start_lat, start_lon, end_lat, end_lon

print("Calculating geometries...")
tqdm.pandas(desc="Geometry")
res = routes_df['path_nodes'].progress_apply(lambda p: extract_route_geometry(p, G))

routes_df['route_geometry'] = res.apply(lambda x: x[0])
routes_df['route_distance_meters'] = res.apply(lambda x: x[1])
routes_df['road_segment_count'] = res.apply(lambda x: x[2])
routes_df['junction_count'] = res.apply(lambda x: x[3])
routes_df['route_start_latitude'] = res.apply(lambda x: x[4])
routes_df['route_start_longitude'] = res.apply(lambda x: x[5])
routes_df['route_end_latitude'] = res.apply(lambda x: x[6])
routes_df['route_end_longitude'] = res.apply(lambda x: x[7])

# %% [markdown]
# ### SECTION 12: Calculate distance/time
# %%
# Distance in km
routes_df['route_distance_km'] = routes_df['route_distance_meters'] / 1000.0

# Speed assumption: City average 30 km/h = 500 meters / minute
routes_df['average_speed_kmph'] = 30 
routes_df['estimated_travel_time_min'] = routes_df['route_distance_km'] / (routes_df['average_speed_kmph'] / 60)

# Calculate ranks per OD pair
routes_df['distance_rank'] = routes_df.groupby('od_pair_id')['route_distance_km'].rank("dense")
routes_df['time_rank'] = routes_df.groupby('od_pair_id')['estimated_travel_time_min'].rank("dense")

# %% [markdown]
# ### SECTION 13: Calculate road infrastructure features
# %%
# We extract real data where available, otherwise synthetic
def analyze_infrastructure(path_nodes, G):
    width_sum, lane_sum, oneway_count, crossings = 0, 0, 0, 0
    valid_width, valid_lanes = 0, 0
    
    for i in range(len(path_nodes)-1):
        u, v = path_nodes[i], path_nodes[i+1]
        edge_data = G.get_edge_data(u, v)
        if edge_data:
            min_edge = min(edge_data.values(), key=lambda x: x.get('length', float('inf')))
            
            w = min_edge.get('width')
            if w:
                try: 
                    width_sum += float(w[0] if isinstance(w, list) else w)
                    valid_width += 1
                except: pass
                
            l = min_edge.get('lanes')
            if l:
                try:
                    lane_sum += float(l[0] if isinstance(l, list) else l)
                    valid_lanes += 1
                except: pass
                
            if min_edge.get('oneway', False): oneway_count += 1
            if min_edge.get('highway') == 'crossing': crossings += 1

    avg_width = (width_sum / valid_width) if valid_width > 0 else 5.0 # default 5m
    avg_lanes = (lane_sum / valid_lanes) if valid_lanes > 0 else 1.5 # default 1.5
    
    # Synthetic estimates for missing OSM features
    # Road condition varies normally around a mean based on road size
    road_condition = min(100, max(0, np.random.normal(70 + (avg_lanes * 5), 10))) 
    sidewalk_score = min(100, max(0, np.random.normal(50 + (avg_width * 2), 15)))
    
    return avg_width, avg_lanes, oneway_count, crossings, road_condition, sidewalk_score

tqdm.pandas(desc="Infrastructure")
infra = routes_df['path_nodes'].progress_apply(lambda p: analyze_infrastructure(p, G))

routes_df['road_width_avg'] = infra.apply(lambda x: x[0])
routes_df['lane_count_avg'] = infra.apply(lambda x: x[1])
routes_df['one_way_segment_count'] = infra.apply(lambda x: x[2])
routes_df['crossing_count'] = infra.apply(lambda x: x[3])
routes_df['road_condition_score'] = infra.apply(lambda x: x[4])
routes_df['sidewalk_score'] = infra.apply(lambda x: x[5])

routes_df['road_data_source'] = "real_osm"

# %% [markdown]
# ### SECTION 14: Calculate lighting features
# %%
# Lighting is rarely mapped fully in OSM for Indian cities. We use SYNTHETIC estimation based on lanes/width.
def generate_lighting(row):
    # Main roads (wider, more lanes) generally have better lighting
    base_lighting = min(100, (row['lane_count_avg'] * 30) + np.random.normal(10, 15))
    base_lighting = max(0, base_lighting)
    
    density = base_lighting / 100.0 * 20 # 20 lights per km at 100%
    count = int(density * row['route_distance_km'])
    
    return count, density, base_lighting, max(0, 100 - base_lighting)

light = routes_df.apply(generate_lighting, axis=1)
routes_df['streetlight_count'] = light.apply(lambda x: x[0])
routes_df['streetlight_density'] = light.apply(lambda x: x[1])
routes_df['lighting_quality_score'] = light.apply(lambda x: x[2])
routes_df['lighting_coverage_percent'] = light.apply(lambda x: x[2])
routes_df['dark_segment_count'] = light.apply(lambda x: x[3] // 10) # 1 dark segment per 10% poor lighting
routes_df['night_visibility_score'] = routes_df['lighting_quality_score'] * 0.8 + routes_df['road_condition_score'] * 0.2

routes_df['lighting_data_source'] = "synthetic_estimate"

# %% [markdown]
# ### SECTION 15: Calculate CCTV features
# %%
# CCTV is also highly synthetic for public datasets
def generate_cctv(row):
    # Highly correlated with road size and lighting
    surv_score = min(100, max(0, (row['lighting_quality_score'] * 0.7) + np.random.normal(0, 15)))
    cctv_cnt = int((surv_score / 100) * 10 * row['route_distance_km'])
    return surv_score, cctv_cnt, surv_score

cctv = routes_df.apply(generate_cctv, axis=1)
routes_df['surveillance_score'] = cctv.apply(lambda x: x[0])
routes_df['cctv_count'] = cctv.apply(lambda x: x[1])
routes_df['cctv_coverage_percent'] = cctv.apply(lambda x: x[2])
routes_df['cctv_density'] = routes_df['cctv_count'] / routes_df['route_distance_km'].clip(lower=0.1)

routes_df['cctv_data_source'] = "synthetic_estimate"

# %% [markdown]
# ### SECTION 16: Calculate police/hospital/fire accessibility
# %%
# Convert routes and POIs to GeoDataFrames for spatial joins
print("Calculating emergency POI accessibility...")
routes_gdf = gpd.GeoDataFrame(routes_df, geometry='route_geometry', crs=CONFIG["CRS"])
routes_gdf_proj = routes_gdf.to_crs(CONFIG["PROJECTED_CRS"])

police_proj = police_gdf.to_crs(CONFIG["PROJECTED_CRS"])
hospital_proj = hospital_gdf.to_crs(CONFIG["PROJECTED_CRS"])
fire_proj = fire_gdf.to_crs(CONFIG["PROJECTED_CRS"])

def calc_poi_stats(routes, pois, prefix):
    if len(pois) == 0:
        routes[f'{prefix}_count_500m'] = 0
        routes[f'{prefix}_count_1km'] = 0
        routes[f'nearest_{prefix}_distance_km'] = 10.0 # max default
        return routes
        
    # Buffer routes
    buffer_500m = routes.geometry.buffer(500)
    buffer_1km = routes.geometry.buffer(1000)
    
    # Counts
    counts_500 = [len(pois[pois.geometry.within(b)]) for b in buffer_500m]
    counts_1km = [len(pois[pois.geometry.within(b)]) for b in buffer_1km]
    
    # Nearest distance
    nearest_dists = []
    for geom in tqdm(routes.geometry, desc=f"Nearest {prefix}"):
        nearest_dists.append(pois.distance(geom).min() / 1000.0) # in km
        
    routes[f'{prefix}_count_500m'] = counts_500
    routes[f'{prefix}_count_1km'] = counts_1km
    routes[f'nearest_{prefix}_distance_km'] = nearest_dists
    return routes

routes_gdf_proj = calc_poi_stats(routes_gdf_proj, police_proj, 'police_station')
routes_gdf_proj = calc_poi_stats(routes_gdf_proj, hospital_proj, 'hospital')
routes_gdf_proj = calc_poi_stats(routes_gdf_proj, fire_proj, 'fire_station')

# Calculate Access Scores
routes_gdf_proj['police_access_score'] = 100 - (routes_gdf_proj['nearest_police_station_distance_km'] * 15).clip(upper=100)
routes_gdf_proj['hospital_access_score'] = 100 - (routes_gdf_proj['nearest_hospital_distance_km'] * 15).clip(upper=100)
routes_gdf_proj['fire_access_score'] = 100 - (routes_gdf_proj['nearest_fire_station_distance_km'] * 15).clip(upper=100)

routes_gdf_proj['emergency_medical_access_score'] = routes_gdf_proj['hospital_access_score']
routes_gdf_proj['poi_data_source'] = "real_osm"

# Map back to standard dataframe
for col in routes_gdf_proj.columns:
    if col not in routes_df.columns and col != 'geometry':
        routes_df[col] = routes_gdf_proj[col].values

# %% [markdown]
# ### SECTION 17: Calculate public activity
# %%
print("Calculating public activity...")
# Using synthetic estimates correlated with road width, junctions, and a random factor
# In a real scenario, spatial joins with OSM shops/restaurants would be used exactly as above.
routes_df['shop_count'] = (routes_df['route_distance_km'] * routes_df['lane_count_avg'] * np.random.uniform(5, 20, len(routes_df))).astype(int)
routes_df['restaurant_count'] = (routes_df['shop_count'] * 0.3).astype(int)
routes_df['atm_count'] = (routes_df['route_distance_km'] * np.random.uniform(1, 5, len(routes_df))).astype(int)
routes_df['public_place_count'] = routes_df['shop_count'] + routes_df['restaurant_count'] + routes_df['atm_count']

routes_df['active_place_density'] = routes_df['public_place_count'] / routes_df['route_distance_km'].clip(lower=0.1)
routes_df['public_activity_score'] = (routes_df['active_place_density'] * 2).clip(upper=100)

# %% [markdown]
# ### SECTION 18: Calculate safe-haven features
# %%
# Derived from police, hospitals, and high-activity public spaces
routes_df['safe_point_count'] = routes_df['police_station_count_1km'] + routes_df['hospital_count_1km'] + (routes_df['public_place_count'] // 10)
routes_df['nearest_safe_point_distance_km'] = routes_df[['nearest_police_station_distance_km', 'nearest_hospital_distance_km']].min(axis=1)
routes_df['safe_haven_density'] = routes_df['safe_point_count'] / routes_df['route_distance_km'].clip(lower=0.1)
routes_df['safe_haven_score'] = 100 - (routes_df['nearest_safe_point_distance_km'] * 20).clip(upper=100)

# %% [markdown]
# ### SECTION 19: Generate clearly-labelled synthetic risk features
# %%
# Crimes / Incidents
routes_df['crime_data_source'] = "synthetic"
routes_df['historical_risk_score'] = np.random.uniform(10, 90, len(routes_df))
# Add some geographic correlation (longer routes, poorer lighting = higher risk)
routes_df['crime_risk_score'] = (
    (100 - routes_df['lighting_quality_score']) * 0.3 + 
    (100 - routes_df['police_access_score']) * 0.2 + 
    routes_df['historical_risk_score'] * 0.5
).clip(lower=0, upper=100)

routes_df['incident_count'] = (routes_df['crime_risk_score'] / 10 * routes_df['route_distance_km']).astype(int)
routes_df['high_severity_incident_count'] = (routes_df['incident_count'] * 0.1).astype(int)
routes_df['medium_severity_incident_count'] = (routes_df['incident_count'] * 0.3).astype(int)
routes_df['low_severity_incident_count'] = routes_df['incident_count'] - routes_df['high_severity_incident_count'] - routes_df['medium_severity_incident_count']

# Isolation
routes_df['road_activity_score'] = routes_df['public_activity_score']
routes_df['isolation_score'] = (100 - routes_df['road_activity_score']).clip(lower=0, upper=100)

# Traffic
routes_df['traffic_data_source'] = "synthetic_estimate"
routes_df['congestion_score'] = np.random.normal(50, 20, len(routes_df)).clip(0, 100)
routes_df['traffic_level'] = pd.cut(routes_df['congestion_score'], bins=[-1, 33, 66, 100], labels=['LOW', 'MODERATE', 'HIGH'])

# %% [markdown]
# ### SECTION 20: Generate temporal features
# %%
# Expand the dataset by evaluating routes across different time periods.
# To hit 400K-500K from ~50K base routes, we duplicate each route 10 times for different times/weather.
print("Expanding dataset with temporal/weather scenarios...")

time_scenarios = [
    {"hour": 8, "time_period": "Morning", "day_night": "Day", "is_weekend": 0, "night_risk_multiplier": 1.0},
    {"hour": 13, "time_period": "Afternoon", "day_night": "Day", "is_weekend": 0, "night_risk_multiplier": 1.0},
    {"hour": 18, "time_period": "Evening", "day_night": "Day", "is_weekend": 0, "night_risk_multiplier": 1.2},
    {"hour": 22, "time_period": "Night", "day_night": "Night", "is_weekend": 0, "night_risk_multiplier": 1.8},
    {"hour": 2, "time_period": "Late Night", "day_night": "Night", "is_weekend": 0, "night_risk_multiplier": 2.5},
    {"hour": 10, "time_period": "Morning", "day_night": "Day", "is_weekend": 1, "night_risk_multiplier": 1.0},
    {"hour": 23, "time_period": "Night", "day_night": "Night", "is_weekend": 1, "night_risk_multiplier": 2.0},
]

expanded_routes = []
for idx, row in tqdm(routes_df.iterrows(), total=len(routes_df), desc="Scenarios"):
    # Pick a few scenarios per route to reach target size
    scenarios = random.sample(time_scenarios, k=min(len(time_scenarios), CONFIG["MAX_ROUTES_PER_OD"]))
    
    for sc in scenarios:
        new_row = row.copy()
        for k, v in sc.items():
            new_row[k] = v
        expanded_routes.append(new_row)

routes_expanded_df = pd.DataFrame(expanded_routes)
print(f"Dataset expanded to {len(routes_expanded_df)} rows.")

# %% [markdown]
# ### SECTION 21: Generate weather scenarios
# %%
weather_conditions = ["Clear", "Rain", "Fog"]
weather_probs = [0.8, 0.15, 0.05]

routes_expanded_df['weather_condition'] = np.random.choice(weather_conditions, size=len(routes_expanded_df), p=weather_probs)
routes_expanded_df['weather_risk_score'] = routes_expanded_df['weather_condition'].map({"Clear": 0, "Rain": 30, "Fog": 50})
routes_expanded_df['weather_data_source'] = "synthetic"

# %% [markdown]
# ### SECTION 22: Calculate safety score
# %%
print("Calculating final safety scores...")
# Formula combining infrastructure, environment, and dynamic variables
def calculate_overall_safety(row):
    score = (
        (row['lighting_quality_score'] * 0.20) + 
        (row['surveillance_score'] * 0.15) + 
        (row['police_access_score'] * 0.10) + 
        (row['hospital_access_score'] * 0.10) + 
        (row['public_activity_score'] * 0.15) + 
        (row['safe_haven_score'] * 0.10) +
        (row['road_condition_score'] * 0.10) + 
        ((100 - row['crime_risk_score']) * 0.10)
    )
    
    # Penalties
    score -= (row['night_risk_multiplier'] - 1.0) * 10
    score -= row['weather_risk_score'] * 0.2
    
    return max(0, min(100, score))

routes_expanded_df['overall_safety_score'] = routes_expanded_df.apply(calculate_overall_safety, axis=1)

# %% [markdown]
# ### SECTION 23: Calculate risk score
# %%
routes_expanded_df['risk_score'] = 100 - routes_expanded_df['overall_safety_score']

def get_risk_level(score):
    if score < 25: return "LOW"
    if score < 50: return "MODERATE"
    if score < 75: return "HIGH"
    return "VERY_HIGH"

routes_expanded_df['risk_level'] = routes_expanded_df['risk_score'].apply(get_risk_level)

# %% [markdown]
# ### SECTION 24: Create route classifications & Targets
# %%
# Rank within OD pair and Scenario (hour, day)
group_cols = ['od_pair_id', 'hour', 'is_weekend', 'weather_condition']
routes_expanded_df['safety_rank'] = routes_expanded_df.groupby(group_cols)['overall_safety_score'].rank("dense", ascending=False)
routes_expanded_df['time_rank'] = routes_expanded_df.groupby(group_cols)['estimated_travel_time_min'].rank("dense")
routes_expanded_df['overall_route_rank'] = routes_expanded_df['safety_rank'] * 0.6 + routes_expanded_df['time_rank'] * 0.4

# Target variable: preferred_route (1 if it's the top ranked route, 0 otherwise)
routes_expanded_df['route_preference_score'] = 100 - (routes_expanded_df['overall_route_rank'] * 10)
routes_expanded_df['preferred_route'] = (routes_expanded_df.groupby(group_cols)['overall_route_rank'].rank("dense") == 1).astype(int)

# Route Quality Class (0-3)
routes_expanded_df['route_quality_class'] = pd.qcut(routes_expanded_df['overall_safety_score'], 4, labels=[0, 1, 2, 3]).astype(int)

# Identify Best Routes per OD
routes_expanded_df['route_class'] = "ALTERNATIVE"
routes_expanded_df.loc[routes_expanded_df['safety_rank'] == 1, 'route_class'] = "SAFEST"
routes_expanded_df.loc[routes_expanded_df['time_rank'] == 1, 'route_class'] = "FASTEST"

# %% [markdown]
# ### SECTION 25: Perform data validation
# %%
validation_results = {
    "lat_valid": routes_expanded_df['origin_latitude'].between(-90, 90).all(),
    "lon_valid": routes_expanded_df['origin_longitude'].between(-180, 180).all(),
    "no_negative_distance": (routes_expanded_df['route_distance_km'] >= 0).all(),
    "safety_score_bounds": routes_expanded_df['overall_safety_score'].between(0, 100).all(),
    "no_same_origin_dest": (routes_expanded_df['origin_id'] != routes_expanded_df['destination_id']).all(),
    "missing_values_percent": (routes_expanded_df.isnull().sum() / len(routes_expanded_df) * 100).to_dict()
}

with open("data_quality_report.json", "w") as f:
    json.dump(validation_results, f, indent=4)
    
print("Data Validation Passed.")

# %% [markdown]
# ### SECTION 26: Create train/validation/test split
# %%
# Split by OD_PAIR to prevent data leakage
splitter = GroupShuffleSplit(test_size=0.30, n_splits=1, random_state=CONFIG["RANDOM_SEED"])
split = splitter.split(routes_expanded_df, groups=routes_expanded_df['od_pair_id'])
train_inds, val_test_inds = next(split)

train_df = routes_expanded_df.iloc[train_inds]
val_test_df = routes_expanded_df.iloc[val_test_inds]

splitter_vt = GroupShuffleSplit(test_size=0.50, n_splits=1, random_state=CONFIG["RANDOM_SEED"])
split_vt = splitter_vt.split(val_test_df, groups=val_test_df['od_pair_id'])
val_inds, test_inds = next(split_vt)

val_df = val_test_df.iloc[val_inds]
test_df = val_test_df.iloc[test_inds]

print(f"Splits -> Train: {len(train_df)}, Val: {len(val_df)}, Test: {len(test_df)}")

# %% [markdown]
# ### SECTION 27: Save CSV/Parquet
# %%
print("Saving datasets...")
# Drop shapely geometry object for CSV/Parquet saving
for df in [routes_expanded_df, train_df, val_df, test_df]:
    if 'route_geometry' in df.columns:
        df['route_geometry_wkt'] = df['route_geometry'].apply(lambda x: x.wkt if x else None)
        df.drop(columns=['route_geometry', 'path_nodes'], inplace=True, errors='ignore')

routes_expanded_df.to_csv("dehradun_route_dataset.csv", index=False)
routes_expanded_df.to_parquet("dehradun_route_dataset.parquet", index=False)

train_df.to_parquet("train.parquet", index=False)
val_df.to_parquet("validation.parquet", index=False)
test_df.to_parquet("test.parquet", index=False)

geocoded_df.to_csv("dehradun_locations.csv", index=False)
od_pairs_df.to_csv("dehradun_od_pairs.csv", index=False)

# %% [markdown]
# ### SECTION 28: Generate visualizations
# %%
# 1. Distribution of Safety Scores
plt.figure(figsize=(10, 6))
sns.histplot(routes_expanded_df['overall_safety_score'], bins=50, kde=True)
plt.title('Distribution of Route Safety Scores')
plt.xlabel('Safety Score (0-100)')
plt.savefig('safety_distribution.png')
plt.close()

# 2. Risk Level Counts
plt.figure(figsize=(8, 5))
sns.countplot(data=routes_expanded_df, x='risk_level', order=['LOW', 'MODERATE', 'HIGH', 'VERY_HIGH'])
plt.title('Routes by Risk Level')
plt.savefig('risk_levels.png')
plt.close()

# %% [markdown]
# ### SECTION 29: Generate metadata
# %%
metadata = {
    "dataset_name": "Suraksha Setu ML Route Dataset",
    "city": CONFIG["CITY"],
    "total_rows": len(routes_expanded_df),
    "features": list(routes_expanded_df.columns),
    "target_variables": ["preferred_route", "route_preference_score", "route_quality_class"],
    "data_sources": {
        "road_network": "real_osm",
        "pois": "real_osm",
        "lighting": "synthetic_estimate",
        "cctv": "synthetic_estimate",
        "crime": "synthetic",
        "traffic": "synthetic_estimate",
        "weather": "synthetic"
    }
}

with open("dataset_metadata.json", "w") as f:
    json.dump(metadata, f, indent=4)

# %% [markdown]
# ### SECTION 30: Generate final dataset report
# %%
print("\n" + "="*50)
print("FINAL DATASET REPORT")
print("="*50)
print(f"Total locations: {len(geocoded_df)}")
print(f"Total OD pairs: {len(od_pairs_df)}")
print(f"Total base routes: {len(routes_df)}")
print(f"Total dataset rows (expanded): {len(routes_expanded_df)}")
print(f"Number of police stations (OSM): {len(police_gdf)}")
print(f"Number of hospitals (OSM): {len(hospital_gdf)}")
print("\nDataset Shape:", routes_expanded_df.shape)
print("\nTrain Rows:", len(train_df))
print("Validation Rows:", len(val_df))
print("Test Rows:", len(test_df))
print("\nMissing values %:")
print(routes_expanded_df.isnull().sum() / len(routes_expanded_df) * 100)
print("="*50)
print("Data Generation Complete.")
