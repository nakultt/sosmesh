from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
import uuid


# ── Location ───────────────────────────────────────────────────────────────────

class LocationInfo(BaseModel):
    lat: float
    lng: float
    accuracy: float
    address: str = ""


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
    route: list[str] = []
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
    route: list[str] = []
    currentHops: int = 0
    maxHops: int = 10
    batteryLevel: int = 100
    relayDeviceId: str
    relayLocation: Optional[LocationInfo] = None
    dedupeKey: str
    relayDeviceIds: list[str] = []
    uploadAttempts: int = 1
    lastRelayDeviceId: str
    lastReceivedAt: str = Field(default_factory=lambda: datetime.utcnow().isoformat())
    status: str = "ACTIVE"  # ACTIVE, RESPONDING, RESOLVED
    createdAt: int  # epoch seconds from device
    receivedAt: str = Field(default_factory=lambda: datetime.utcnow().isoformat())
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
