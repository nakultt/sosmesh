from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from typing import Optional
import logging
import hashlib
from datetime import datetime
from pymongo.errors import DuplicateKeyError

from app.database import get_database, close_database
from app.models import (
    UploadRequest, UploadResponse, Alert,
    StatusUpdate, DashboardStats,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("meshsos-api")
MAX_SERVER_ROUTE_POINTS = 20


# ── Lifespan ───────────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    db = await get_database()
    # Create indexes
    await db.alerts.create_index("alertId", unique=True)
    try:
        await db.alerts.create_index("packetId", unique=True)
    except Exception as e:
        logger.warning(f"Could not enforce unique packetId index: {e}")
    try:
        await db.alerts.create_index(
            "dedupeKey",
            unique=True,
            partialFilterExpression={"dedupeKey": {"$exists": True}},
        )
    except Exception as e:
        logger.warning(f"Could not enforce unique dedupeKey index: {e}")
    await db.alerts.create_index("status")
    await db.alerts.create_index("createdAt")
    logger.info("MongoDB connected and indexed")
    yield
    await close_database()
    logger.info("MongoDB disconnected")


app = FastAPI(
    title="MeshSOS Emergency Server",
    description="Receives SOS packets from the MeshSOS mesh network and manages emergency alerts",
    version="1.0.0",
    lifespan=lifespan,
)

# ── CORS ───────────────────────────────────────────────────────────────────────

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── Health ─────────────────────────────────────────────────────────────────────

@app.get("/")
async def root():
    return {"service": "MeshSOS Emergency Server", "status": "running"}


@app.get("/health")
async def health():
    db = await get_database()
    try:
        await db.command("ping")
        return {"status": "healthy", "database": "connected"}
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Database unhealthy: {e}")


# ── POST /api/emergency/sos — Main endpoint for Android app ───────────────────

@app.post("/api/emergency/sos", response_model=UploadResponse)
async def receive_sos(request: UploadRequest):
    db = await get_database()
    packet = request.packet
    now_iso = datetime.utcnow().isoformat()
    dedupe_key = build_dedupe_key(packet)
    safe_route = sanitize_route(
        route_points=packet.metadata.route,
        max_hops=packet.metadata.maxHops,
        default_timestamp=packet.metadata.createdAt,
    )

    logger.info(
        f"SOS received: id={packet.id} sender={packet.senderId} "
        f"category={packet.incident.category} severity={packet.incident.severity} "
        f"hops={packet.metadata.currentHops} relay={request.relayDeviceId}"
    )

    # Check for duplicate packet (packet ID or content fingerprint)
    existing = await db.alerts.find_one({
        "$or": [
            {"packetId": packet.id},
            {"dedupeKey": dedupe_key},
        ]
    })
    if existing:
        await register_duplicate_upload(
            db=db,
            alert_id=existing["alertId"],
            relay_device_id=request.relayDeviceId,
            now_iso=now_iso,
        )
        logger.info(f"Duplicate packet {packet.id} — returning existing alert")
        return UploadResponse(
            success=True,
            alertId=existing["alertId"],
            respondersNotified=existing.get("respondersNotified", 0),
            estimatedArrival=existing.get("estimatedArrival", ""),
            deduplicated=True,
        )

    # Build alert document
    alert = Alert(
        packetId=packet.id,
        senderId=packet.senderId,
        severity=packet.incident.severity,
        category=packet.incident.category,
        message=packet.incident.message,
        location=packet.incident.location,
        route=safe_route,
        currentHops=packet.metadata.currentHops,
        maxHops=packet.metadata.maxHops,
        batteryLevel=packet.metadata.batteryLevel,
        relayDeviceId=request.relayDeviceId,
        relayLocation=request.relayLocation,
        dedupeKey=dedupe_key,
        relayDeviceIds=[request.relayDeviceId],
        uploadAttempts=1,
        lastRelayDeviceId=request.relayDeviceId,
        lastReceivedAt=now_iso,
        createdAt=packet.metadata.createdAt,
        respondersNotified=1,  # simulated
        estimatedArrival="~5 min",
    )

    try:
        await db.alerts.insert_one(alert.model_dump())
    except DuplicateKeyError:
        # Concurrent insert race: fetch winner and return dedup response.
        winner = await db.alerts.find_one({
            "$or": [
                {"packetId": packet.id},
                {"dedupeKey": dedupe_key},
            ]
        })
        if winner is None:
            raise HTTPException(status_code=500, detail="Duplicate insert race without winner document")
        await register_duplicate_upload(
            db=db,
            alert_id=winner["alertId"],
            relay_device_id=request.relayDeviceId,
            now_iso=now_iso,
        )
        return UploadResponse(
            success=True,
            alertId=winner["alertId"],
            respondersNotified=winner.get("respondersNotified", 0),
            estimatedArrival=winner.get("estimatedArrival", ""),
            deduplicated=True,
        )

    logger.info(f"Alert created: {alert.alertId} for packet {packet.id}")

    return UploadResponse(
        success=True,
        alertId=alert.alertId,
        respondersNotified=alert.respondersNotified,
        estimatedArrival=alert.estimatedArrival,
        deduplicated=False,
    )


# ── GET /api/alerts — List alerts ─────────────────────────────────────────────

@app.get("/api/alerts")
async def list_alerts(
    status: Optional[str] = Query(None, description="Filter by status: ACTIVE, RESPONDING, RESOLVED"),
    severity: Optional[str] = Query(None, description="Filter by severity: CRITICAL, HIGH, MEDIUM"),
    category: Optional[str] = Query(None, description="Filter by category: MEDICAL, FIRE, etc."),
    limit: int = Query(50, ge=1, le=200),
    skip: int = Query(0, ge=0),
):
    db = await get_database()

    query = {}
    if status:
        query["status"] = status.upper()
    if severity:
        query["severity"] = severity.upper()
    if category:
        query["category"] = category.upper()

    cursor = db.alerts.find(query, {"_id": 0}).sort("createdAt", -1).skip(skip).limit(limit)
    alerts = await cursor.to_list(length=limit)

    total = await db.alerts.count_documents(query)

    return {"alerts": alerts, "total": total, "limit": limit, "skip": skip}


# ── GET /api/alerts/{alert_id} — Single alert ────────────────────────────────

@app.get("/api/alerts/{alert_id}")
async def get_alert(alert_id: str):
    db = await get_database()
    alert = await db.alerts.find_one({"alertId": alert_id}, {"_id": 0})
    if not alert:
        raise HTTPException(status_code=404, detail=f"Alert {alert_id} not found")
    return alert


# ── PATCH /api/alerts/{alert_id}/status — Update status ──────────────────────

@app.patch("/api/alerts/{alert_id}/status")
async def update_alert_status(alert_id: str, update: StatusUpdate):
    db = await get_database()

    valid_statuses = {"ACTIVE", "RESPONDING", "RESOLVED"}
    if update.status.upper() not in valid_statuses:
        raise HTTPException(
            status_code=400,
            detail=f"Invalid status. Must be one of: {valid_statuses}"
        )

    result = await db.alerts.update_one(
        {"alertId": alert_id},
        {"$set": {"status": update.status.upper()}}
    )

    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail=f"Alert {alert_id} not found")

    logger.info(f"Alert {alert_id} status updated to {update.status.upper()}")
    return {"success": True, "alertId": alert_id, "status": update.status.upper()}


# ── GET /api/stats — Dashboard statistics ────────────────────────────────────

@app.get("/api/stats", response_model=DashboardStats)
async def get_stats():
    db = await get_database()

    total = await db.alerts.count_documents({})
    active = await db.alerts.count_documents({"status": "ACTIVE"})
    responding = await db.alerts.count_documents({"status": "RESPONDING"})
    resolved = await db.alerts.count_documents({"status": "RESOLVED"})

    # Aggregate by category
    category_pipeline = [
        {"$group": {"_id": "$category", "count": {"$sum": 1}}}
    ]
    category_cursor = db.alerts.aggregate(category_pipeline)
    by_category = {doc["_id"]: doc["count"] async for doc in category_cursor}

    # Aggregate by severity
    severity_pipeline = [
        {"$group": {"_id": "$severity", "count": {"$sum": 1}}}
    ]
    severity_cursor = db.alerts.aggregate(severity_pipeline)
    by_severity = {doc["_id"]: doc["count"] async for doc in severity_cursor}

    return DashboardStats(
        total=total,
        active=active,
        responding=responding,
        resolved=resolved,
        byCategory=by_category,
        bySeverity=by_severity,
    )


# ── DELETE /api/alerts/{alert_id} — Remove alert ─────────────────────────────

@app.delete("/api/alerts/{alert_id}")
async def delete_alert(alert_id: str):
    db = await get_database()
    result = await db.alerts.delete_one({"alertId": alert_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail=f"Alert {alert_id} not found")
    return {"success": True, "deleted": alert_id}


def build_dedupe_key(packet) -> str:
    """Create stable SOS fingerprint for cross-device deduplication."""
    location = packet.incident.location
    location_key = ""
    if location is not None:
        location_key = f"{round(location.lat, 4)}:{round(location.lng, 4)}"

    base = "|".join([
        packet.senderId,
        str(packet.metadata.createdAt),
        packet.incident.category,
        packet.incident.severity,
        packet.incident.message.strip().lower(),
        location_key,
    ])
    return hashlib.sha256(base.encode("utf-8")).hexdigest()


async def register_duplicate_upload(db, alert_id: str, relay_device_id: str, now_iso: str) -> None:
    await db.alerts.update_one(
        {"alertId": alert_id},
        {
            "$addToSet": {"relayDeviceIds": relay_device_id},
            "$inc": {"uploadAttempts": 1},
            "$set": {
                "lastRelayDeviceId": relay_device_id,
                "lastReceivedAt": now_iso,
            },
        }
    )


def sanitize_route(route_points, max_hops: int, default_timestamp: int):
    route_limit = min(max(max_hops + 1, 2), MAX_SERVER_ROUTE_POINTS)
    if not route_points:
        return []

    # Keep most recent points and normalize to plain dict for Mongo storage.
    trimmed = route_points[-route_limit:]
    normalized = []
    for point in trimmed:
        if isinstance(point, str):
            normalized.append({
                "deviceId": point,
                "location": None,
                "timestamp": default_timestamp,
            })
        elif hasattr(point, "model_dump"):
            normalized.append(point.model_dump())
        elif isinstance(point, dict):
            normalized.append(point)
    return normalized


# ── Run ────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    from app.database import get_settings

    settings = get_settings()
    uvicorn.run("app.main:app", host=settings.host, port=settings.port, reload=True)
