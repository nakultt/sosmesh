from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime, timezone
import uuid


# ── Location ───────────────────────────────────────────────────────────────────

class LocationInfo(BaseModel):
    lat: float
    lng: float
    accuracy: float
    address: str = ""


class RoutePoint(BaseModel):
    deviceId: str
    location: Optional[LocationInfo] = None
    timestamp: int


# ── Incident ───────────────────────────────────────────────────────────────────

class IncidentInfo(BaseModel):
    severity: str = "CRITICAL"  # CRITICAL, HIGH, MEDIUM
    category: str = "OTHER"    # MEDICAL, FIRE, VIOLENCE, NATURAL_DISASTER, OTHER
    message: str = ""
    location: Optional[LocationInfo] = None


# ── Packet metadata ────────────────────────────────────────────────────────────

class PacketMetadata(BaseModel):
    createdAt: int  # epoch seconds
    ttl: int = 3600
    maxHops: int = 10
    currentHops: int = 0
    route: list[RoutePoint | str] = Field(default_factory=list)
    batteryLevel: int = 100


# ── SOS Packet (from Android app) ─────────────────────────────────────────────

class SosPacket(BaseModel):
    id: str
    type: str = "SOS"
    senderId: str
    incident: IncidentInfo
    metadata: PacketMetadata
    uploaded: bool = False
    uploadTimestamp: Optional[int] = None


# ── Upload request (what Android POST body looks like) ─────────────────────────

class UploadRequest(BaseModel):
    packet: SosPacket
    relayDeviceId: str
    relayLocation: Optional[LocationInfo] = None


# ── Upload response (what Android expects back) ───────────────────────────────

class UploadResponse(BaseModel):
    success: bool
    alertId: str
    respondersNotified: int = 0
    estimatedArrival: str = ""
    deduplicated: bool = False


# ── Alert (stored in MongoDB) ─────────────────────────────────────────────────

class Alert(BaseModel):
    alertId: str = Field(default_factory=lambda: f"ALERT-{uuid.uuid4().hex[:8].upper()}")
    packetId: str
    senderId: str
    severity: str
    category: str
    message: str
    location: Optional[LocationInfo] = None
    route: list[RoutePoint | str] = Field(default_factory=list)
    currentHops: int = 0
    maxHops: int = 10
    batteryLevel: int = 100
    relayDeviceId: str
    relayLocation: Optional[LocationInfo] = None
    dedupeKey: str
    relayDeviceIds: list[str] = Field(default_factory=list)
    uploadAttempts: int = 1
    lastRelayDeviceId: str
    lastReceivedAt: str = Field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    status: str = "ACTIVE"  # ACTIVE, RESPONDING, RESOLVED
    createdAt: int  # epoch seconds from device
    receivedAt: str = Field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    respondersNotified: int = 0
    estimatedArrival: str = ""


# ── Status update ─────────────────────────────────────────────────────────────

class StatusUpdate(BaseModel):
    status: str  # ACTIVE, RESPONDING, RESOLVED


# ── Stats ─────────────────────────────────────────────────────────────────────

class DashboardStats(BaseModel):
    total: int = 0
    active: int = 0
    responding: int = 0
    resolved: int = 0
    byCategory: dict[str, int] = {}
    bySeverity: dict[str, int] = {}
