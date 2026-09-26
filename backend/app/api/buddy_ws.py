import json
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from typing import Dict, List, Any

router = APIRouter()

# A simple queue of waiting users
waiting_users: List[WebSocket] = []
user_info: Dict[WebSocket, Dict[str, Any]] = {}

@router.websocket("/ws/buddy")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    try:
        while True:
            data = await websocket.receive_text()
            message = json.loads(data)
            
            action = message.get("action")
            
            if action == "request_buddy":
                name = message.get("name", "Unknown")
                lat = message.get("lat", 0.0)
                lng = message.get("lng", 0.0)
                
                user_info[websocket] = {"name": name, "lat": lat, "lng": lng}
                
                # Check if someone is already waiting
                if waiting_users:
                    # Pop the first waiting user
                    partner_ws = waiting_users.pop(0)
                    partner_data = user_info.get(partner_ws, {})
                    
                    # Send match to the new user
                    await websocket.send_json({
                        "action": "buddy_found",
                        "buddy_name": partner_data.get("name", "Volunteer"),
                        "distance": "approx 250m"
                    })
                    
                    # Send match to the waiting user
                    try:
                        await partner_ws.send_json({
                            "action": "buddy_found",
                            "buddy_name": name,
                            "distance": "approx 250m"
                        })
                    except Exception:
                        pass # Partner disconnected
                else:
                    # No one waiting, join the queue
                    waiting_users.append(websocket)
                    
    except WebSocketDisconnect:
        if websocket in waiting_users:
            waiting_users.remove(websocket)
        if websocket in user_info:
            del user_info[websocket]
