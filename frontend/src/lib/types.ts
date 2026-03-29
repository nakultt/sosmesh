// ── Location ──────────────────────────────────────────────────────────────────

export interface LocationInfo {
  lat: number;
  lng: number;
  accuracy: number;
  address: string;
}

// ── Alert (from backend) ─────────────────────────────────────────────────────

export interface Alert {
  alertId: string;
  packetId: string;
  senderId: string;
  severity: "CRITICAL" | "HIGH" | "MEDIUM";
  category: "MEDICAL" | "FIRE" | "VIOLENCE" | "NATURAL_DISASTER" | "OTHER";
  message: string;
  location: LocationInfo | null;
  route: string[];
  currentHops: number;
  maxHops: number;
  batteryLevel: number;
  relayDeviceId: string;
  relayLocation: LocationInfo | null;
  status: "ACTIVE" | "RESPONDING" | "RESOLVED";
  createdAt: number;
  receivedAt: string;
  respondersNotified: number;
  estimatedArrival: string;
}

// ── Stats ────────────────────────────────────────────────────────────────────

export interface DashboardStats {
  total: number;
  active: number;
  responding: number;
  resolved: number;
  byCategory: Record<string, number>;
  bySeverity: Record<string, number>;
}

// ── API response wrappers ────────────────────────────────────────────────────

export interface AlertsResponse {
  alerts: Alert[];
  total: number;
  limit: number;
  skip: number;
}
