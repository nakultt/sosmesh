import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { getAlert, updateAlertStatus, deleteAlert } from "../lib/api";
import type { Alert } from "../lib/types";
import StatusBadge from "../components/StatusBadge";
import HopRoute from "../components/HopRoute";
import AlertMap from "../components/AlertMap";
import {
  ArrowLeft,
  Clock,
  MapPin,
  Wifi,
  Battery,
  User,
  Radio,
  Trash2,
  Heart,
  Flame,
  AlertTriangle,
  Cloud,
  HelpCircle,
} from "lucide-react";

const categoryIcons: Record<string, typeof Heart> = {
  MEDICAL: Heart,
  FIRE: Flame,
  VIOLENCE: AlertTriangle,
  NATURAL_DISASTER: Cloud,
  OTHER: HelpCircle,
};

function formatTime(epochSeconds: number): string {
  return new Date(epochSeconds * 1000).toLocaleString();
}

export default function AlertDetail() {
  const { alertId } = useParams<{ alertId: string }>();
  const navigate = useNavigate();
  const [alert, setAlert] = useState<Alert | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [updating, setUpdating] = useState(false);

  useEffect(() => {
    if (!alertId) return;
    let cancelled = false;
    const fetchAlert = async () => {
      try {
        const data = await getAlert(alertId);
        if (!cancelled) setAlert(data);
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load");
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    fetchAlert();
    return () => { cancelled = true; };
  }, [alertId]);

  const handleStatusChange = async (newStatus: string) => {
    if (!alertId || !alert) return;
    setUpdating(true);
    try {
      await updateAlertStatus(alertId, newStatus);
      setAlert({ ...alert, status: newStatus as Alert["status"] });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Update failed");
    }
    setUpdating(false);
  };

  const handleDelete = async () => {
    if (!alertId || !confirm("Delete this alert permanently?")) return;
    try {
      await deleteAlert(alertId);
      navigate("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed");
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-bg-primary flex items-center justify-center">
        <div className="text-text-muted animate-pulse">Loading alert…</div>
      </div>
    );
  }

  if (error || !alert) {
    return (
      <div className="min-h-screen bg-bg-primary flex items-center justify-center">
        <div className="text-center">
          <p className="text-sos-red mb-4">{error || "Alert not found"}</p>
          <button
            onClick={() => navigate("/")}
            className="text-mesh-teal hover:underline text-sm"
          >
            ← Back to Dashboard
          </button>
        </div>
      </div>
    );
  }

  const Icon = categoryIcons[alert.category] ?? HelpCircle;

  return (
    <div className="min-h-screen bg-bg-primary">
      {/* Header */}
      <header className="border-b border-border bg-bg-secondary/80 backdrop-blur-md sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-4">
          <div className="flex items-center justify-between">
            <button
              onClick={() => navigate("/")}
              className="flex items-center gap-2 text-text-secondary hover:text-text-primary transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              <span className="text-sm">Back</span>
            </button>
            <div className="flex items-center gap-2">
              <StatusBadge status={alert.status} size="md" />
              <button
                onClick={handleDelete}
                className="p-2 rounded-lg border border-border hover:bg-sos-red/10 hover:border-sos-red/30 transition-colors"
                title="Delete alert"
              >
                <Trash2 className="w-4 h-4 text-text-muted hover:text-sos-red" />
              </button>
            </div>
          </div>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 sm:px-6 py-6 space-y-6">
        {/* Alert header card */}
        <div className="bg-bg-card border border-border rounded-xl p-6">
          <div className="flex items-start gap-4 mb-4">
            <div
              className={`p-3 rounded-xl ${
                alert.severity === "CRITICAL"
                  ? "bg-sos-red/15"
                  : alert.severity === "HIGH"
                    ? "bg-warn-amber/15"
                    : "bg-mesh-teal/15"
              }`}
            >
              <Icon
                className={`w-6 h-6 ${
                  alert.severity === "CRITICAL"
                    ? "text-sos-red"
                    : alert.severity === "HIGH"
                      ? "text-warn-amber"
                      : "text-mesh-teal"
                }`}
              />
            </div>
            <div className="flex-1">
              <h1 className="text-xl font-bold text-text-primary">
                {alert.category.replace("_", " ")} Alert
              </h1>
              <p className="font-mono text-sm text-text-muted mt-0.5">
                {alert.alertId}
              </p>
              {alert.message && (
                <p className="text-text-secondary mt-2">{alert.message}</p>
              )}
            </div>
          </div>

          {/* Meta grid */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mt-4">
            <MetaItem
              icon={Clock}
              label="Created"
              value={formatTime(alert.createdAt)}
            />
            <MetaItem
              icon={User}
              label="Sender"
              value={alert.senderId}
              mono
            />
            <MetaItem
              icon={Battery}
              label="Battery"
              value={`${alert.batteryLevel}%`}
            />
            <MetaItem
              icon={Wifi}
              label="Hops"
              value={`${alert.currentHops} / ${alert.maxHops}`}
            />
          </div>
        </div>

        {/* Status controls */}
        <div className="bg-bg-card border border-border rounded-xl p-5">
          <h2 className="text-xs font-semibold text-text-muted uppercase tracking-wide mb-3">
            Update Status
          </h2>
          <div className="flex gap-3">
            {(["ACTIVE", "RESPONDING", "RESOLVED"] as const).map((s) => (
              <button
                key={s}
                onClick={() => handleStatusChange(s)}
                disabled={updating || alert.status === s}
                className={`px-4 py-2 rounded-lg text-sm font-medium border transition-all ${
                  alert.status === s
                    ? s === "ACTIVE"
                      ? "bg-sos-red/20 border-sos-red/40 text-sos-red"
                      : s === "RESPONDING"
                        ? "bg-warn-amber/20 border-warn-amber/40 text-warn-amber"
                        : "bg-safe-green/20 border-safe-green/40 text-safe-green"
                    : "bg-bg-hover border-border text-text-secondary hover:bg-bg-card disabled:opacity-40"
                }`}
              >
                {s}
              </button>
            ))}
          </div>
        </div>

        {/* Hop route */}
        <div className="bg-bg-card border border-border rounded-xl p-5">
          <h2 className="text-xs font-semibold text-text-muted uppercase tracking-wide mb-3">
            Mesh Relay Route
          </h2>
          <HopRoute
            route={alert.route}
            senderId={alert.senderId}
            relayDeviceId={alert.relayDeviceId}
          />
          <p className="text-xs text-text-muted mt-3">
            Packet traveled through {alert.currentHops} device
            {alert.currentHops !== 1 ? "s" : ""} before reaching the server.
            Uploaded by relay device{" "}
            <span className="font-mono text-safe-green">
              {alert.relayDeviceId}
            </span>
            .
          </p>
        </div>

        {/* Map + location */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="bg-bg-card border border-border rounded-xl p-5">
            <h2 className="text-xs font-semibold text-text-muted uppercase tracking-wide mb-3">
              Location
            </h2>
            <AlertMap
              location={alert.location}
              route={alert.route}
              senderId={alert.senderId}
              relayLocation={alert.relayLocation}
              className="h-64"
            />
            {alert.location && (
              <div className="mt-3 space-y-1 text-xs">
                <div className="flex items-center gap-2 text-text-secondary">
                  <MapPin className="w-3 h-3 text-sos-red" />
                  <span className="font-mono">
                    {alert.location.lat.toFixed(6)},{" "}
                    {alert.location.lng.toFixed(6)}
                  </span>
                </div>
                {alert.location.address && (
                  <p className="text-text-muted pl-5">
                    {alert.location.address}
                  </p>
                )}
                <div className="flex items-center gap-2 text-text-muted">
                  <Radio className="w-3 h-3" />
                  <span>Accuracy: ±{alert.location.accuracy?.toFixed(0)}m</span>
                </div>
              </div>
            )}
          </div>

          {/* Raw data */}
          <div className="bg-bg-card border border-border rounded-xl p-5">
            <h2 className="text-xs font-semibold text-text-muted uppercase tracking-wide mb-3">
              Raw Packet Data
            </h2>
            <pre className="text-xs text-text-secondary font-mono bg-bg-primary/50 rounded-lg p-4 overflow-auto max-h-80">
              {JSON.stringify(alert, null, 2)}
            </pre>
          </div>
        </div>
      </main>
    </div>
  );
}

// ── Helper ─────────────────────────────────────────────────────────────────────

function MetaItem({
  icon: Icon,
  label,
  value,
  mono = false,
}: {
  icon: typeof Clock;
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="bg-bg-hover/50 rounded-lg p-3">
      <div className="flex items-center gap-1.5 text-text-muted mb-1">
        <Icon className="w-3 h-3" />
        <span className="text-xs">{label}</span>
      </div>
      <p
        className={`text-sm text-text-primary ${mono ? "font-mono" : ""} truncate`}
      >
        {value}
      </p>
    </div>
  );
}
