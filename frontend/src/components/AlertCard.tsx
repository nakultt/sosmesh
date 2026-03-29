import type { Alert } from "../lib/types";
import StatusBadge from "./StatusBadge";
import { Link } from "react-router-dom";
import {
  MapPin,
  Clock,
  Wifi,
  Battery,
  Heart,
  Flame,
  AlertTriangle,
  Cloud,
  HelpCircle,
} from "lucide-react";

interface Props {
  alert: Alert;
}

const categoryIcons: Record<string, typeof Heart> = {
  MEDICAL: Heart,
  FIRE: Flame,
  VIOLENCE: AlertTriangle,
  NATURAL_DISASTER: Cloud,
  OTHER: HelpCircle,
};

const categoryColors: Record<string, string> = {
  MEDICAL: "text-sos-red",
  FIRE: "text-warn-amber",
  VIOLENCE: "text-sos-red",
  NATURAL_DISASTER: "text-mesh-teal",
  OTHER: "text-subtle-gray",
};

function timeAgo(epochSeconds: number): string {
  const now = Math.floor(Date.now() / 1000);
  const diff = now - epochSeconds;
  if (diff < 60) return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
}

export default function AlertCard({ alert }: Props) {
  const Icon = categoryIcons[alert.category] ?? HelpCircle;
  const colorClass = categoryColors[alert.category] ?? "text-subtle-gray";

  return (
    <Link to={`/alert/${alert.alertId}`} className="block">
      <div
        className={`bg-bg-card border border-border rounded-xl p-4 transition-all duration-200 hover:bg-bg-hover hover:border-border/80 hover:scale-[1.01] cursor-pointer animate-fade-in-up ${
          alert.status === "ACTIVE" ? "animate-pulse-glow" : ""
        }`}
      >
        {/* Header */}
        <div className="flex items-start justify-between mb-3">
          <div className="flex items-center gap-2.5">
            <div
              className={`p-2 rounded-lg ${
                alert.severity === "CRITICAL"
                  ? "bg-sos-red/15"
                  : alert.severity === "HIGH"
                    ? "bg-warn-amber/15"
                    : "bg-mesh-teal/15"
              }`}
            >
              <Icon className={`w-4 h-4 ${colorClass}`} />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-text-primary">
                  {alert.category.replace("_", " ")}
                </span>
                <span
                  className={`text-xs font-mono px-1.5 py-0.5 rounded ${
                    alert.severity === "CRITICAL"
                      ? "bg-sos-red/15 text-sos-red"
                      : alert.severity === "HIGH"
                        ? "bg-warn-amber/15 text-warn-amber"
                        : "bg-mesh-teal/15 text-mesh-teal"
                  }`}
                >
                  {alert.severity}
                </span>
              </div>
              <p className="text-xs text-text-muted font-mono mt-0.5">
                {alert.alertId}
              </p>
            </div>
          </div>
          <StatusBadge status={alert.status} />
        </div>

        {/* Message */}
        {alert.message && (
          <p className="text-sm text-text-secondary mb-3 line-clamp-2">
            {alert.message}
          </p>
        )}

        {/* Footer */}
        <div className="flex items-center gap-4 text-xs text-text-muted">
          <span className="flex items-center gap-1">
            <Clock className="w-3 h-3" />
            {timeAgo(alert.createdAt)}
          </span>
          {alert.location && (
            <span className="flex items-center gap-1">
              <MapPin className="w-3 h-3" />
              {alert.location.lat.toFixed(4)}, {alert.location.lng.toFixed(4)}
            </span>
          )}
          <span className="flex items-center gap-1">
            <Wifi className="w-3 h-3" />
            {alert.currentHops} hop{alert.currentHops !== 1 ? "s" : ""}
          </span>
          <span className="flex items-center gap-1">
            <Battery className="w-3 h-3" />
            {alert.batteryLevel}%
          </span>
        </div>
      </div>
    </Link>
  );
}
