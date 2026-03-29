import type { DashboardStats } from "../lib/types";
import { AlertTriangle, Radio, Shield, CheckCircle } from "lucide-react";

interface Props {
  stats: DashboardStats | null;
  loading: boolean;
}

const cards = [
  {
    key: "total" as const,
    label: "Total Alerts",
    icon: AlertTriangle,
    color: "text-mesh-teal",
    bgColor: "bg-mesh-teal/10",
    borderColor: "border-mesh-teal/20",
  },
  {
    key: "active" as const,
    label: "Active",
    icon: Radio,
    color: "text-sos-red",
    bgColor: "bg-sos-red/10",
    borderColor: "border-sos-red/20",
  },
  {
    key: "responding" as const,
    label: "Responding",
    icon: Shield,
    color: "text-warn-amber",
    bgColor: "bg-warn-amber/10",
    borderColor: "border-warn-amber/20",
  },
  {
    key: "resolved" as const,
    label: "Resolved",
    icon: CheckCircle,
    color: "text-safe-green",
    bgColor: "bg-safe-green/10",
    borderColor: "border-safe-green/20",
  },
];

export default function StatsGrid({ stats, loading }: Props) {
  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      {cards.map((card) => (
        <div
          key={card.key}
          className={`${card.bgColor} ${card.borderColor} border rounded-xl p-4 backdrop-blur-sm transition-all duration-200 hover:scale-[1.02]`}
        >
          <div className="flex items-center justify-between mb-2">
            <card.icon className={`w-5 h-5 ${card.color}`} />
            <span className={`text-2xl font-bold font-mono ${card.color}`}>
              {loading ? "—" : stats?.[card.key] ?? 0}
            </span>
          </div>
          <p className="text-text-secondary text-sm">{card.label}</p>
        </div>
      ))}
    </div>
  );
}
