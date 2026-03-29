import { useEffect, useState, useCallback } from "react";
import { getAlerts, getStats } from "../lib/api";
import type { Alert, DashboardStats } from "../lib/types";
import StatsGrid from "../components/StatsGrid";
import AlertCard from "../components/AlertCard";
import { RefreshCw, Radio, Filter } from "lucide-react";

const REFRESH_INTERVAL = 10_000;

const categoryOptions = [
  "ALL",
  "MEDICAL",
  "FIRE",
  "VIOLENCE",
  "NATURAL_DISASTER",
  "OTHER",
];
const statusOptions = ["ALL", "ACTIVE", "RESPONDING", "RESOLVED"];

export default function Dashboard() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filterCategory, setFilterCategory] = useState("ALL");
  const [filterStatus, setFilterStatus] = useState("ALL");
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date());

  const fetchData = useCallback(async () => {
    try {
      const params: Record<string, string> = {};
      if (filterCategory !== "ALL") params.category = filterCategory;
      if (filterStatus !== "ALL") params.status = filterStatus;

      const [alertsData, statsData] = await Promise.all([
        getAlerts({ ...params, limit: 50 }),
        getStats(),
      ]);
      setAlerts(alertsData.alerts);
      setStats(statsData);
      setError(null);
      setLastRefresh(new Date());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch data");
    } finally {
      setLoading(false);
    }
  }, [filterCategory, filterStatus]);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, REFRESH_INTERVAL);
    return () => clearInterval(interval);
  }, [fetchData]);

  return (
    <div className="min-h-screen bg-bg-primary">
      {/* Header */}
      <header className="border-b border-border bg-bg-secondary/80 backdrop-blur-md sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-sos-red/15">
                <Radio className="w-5 h-5 text-sos-red" />
              </div>
              <div>
                <h1 className="text-lg font-bold text-text-primary">
                  MeshSOS Command Center
                </h1>
                <p className="text-xs text-text-muted">
                  Emergency Alert Dashboard
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <span className="text-xs text-text-muted font-mono hidden sm:block">
                Last refresh: {lastRefresh.toLocaleTimeString()}
              </span>
              <button
                onClick={fetchData}
                className="p-2 rounded-lg border border-border bg-bg-card hover:bg-bg-hover transition-colors"
                title="Refresh"
              >
                <RefreshCw
                  className={`w-4 h-4 text-text-secondary ${loading ? "animate-spin" : ""}`}
                />
              </button>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-safe-green animate-pulse" />
                <span className="text-xs text-safe-green font-medium">
                  LIVE
                </span>
              </div>
            </div>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 py-6 space-y-6">
        {/* Error banner */}
        {error && (
          <div className="bg-sos-red/10 border border-sos-red/30 rounded-xl p-4 text-sos-red text-sm">
            <strong>Connection Error:</strong> {error}
            <p className="text-xs text-text-muted mt-1">
              Make sure the backend is running on{" "}
              {import.meta.env.VITE_API_URL || "http://localhost:8000"}
            </p>
          </div>
        )}

        {/* Stats */}
        <StatsGrid stats={stats} loading={loading} />

        {/* Filters */}
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1.5 text-text-muted text-sm">
            <Filter className="w-4 h-4" />
            <span>Filters:</span>
          </div>

          <div className="flex gap-1.5 flex-wrap">
            {statusOptions.map((s) => (
              <button
                key={s}
                onClick={() => setFilterStatus(s)}
                className={`px-3 py-1 rounded-lg text-xs font-medium border transition-colors ${
                  filterStatus === s
                    ? "bg-mesh-teal/15 border-mesh-teal/40 text-mesh-teal"
                    : "bg-bg-card border-border text-text-secondary hover:bg-bg-hover"
                }`}
              >
                {s}
              </button>
            ))}
          </div>

          <div className="w-px h-5 bg-border hidden sm:block" />

          <div className="flex gap-1.5 flex-wrap">
            {categoryOptions.map((c) => (
              <button
                key={c}
                onClick={() => setFilterCategory(c)}
                className={`px-3 py-1 rounded-lg text-xs font-medium border transition-colors ${
                  filterCategory === c
                    ? "bg-warn-amber/15 border-warn-amber/40 text-warn-amber"
                    : "bg-bg-card border-border text-text-secondary hover:bg-bg-hover"
                }`}
              >
                {c.replace("_", " ")}
              </button>
            ))}
          </div>
        </div>

        {/* Alert feed */}
        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-semibold text-text-secondary uppercase tracking-wide">
              Alert Feed
            </h2>
            <span className="text-xs text-text-muted font-mono">
              {alerts.length} alert{alerts.length !== 1 ? "s" : ""}
            </span>
          </div>

          {loading && alerts.length === 0 ? (
            <div className="text-center py-16 text-text-muted">
              <RefreshCw className="w-8 h-8 mx-auto mb-3 animate-spin opacity-50" />
              <p>Loading alerts…</p>
            </div>
          ) : alerts.length === 0 ? (
            <div className="text-center py-16 border border-border/50 rounded-xl bg-bg-card/50">
              <Radio className="w-10 h-10 mx-auto mb-3 text-text-muted opacity-30" />
              <p className="text-text-muted">No alerts yet</p>
              <p className="text-xs text-text-muted mt-1">
                Alerts will appear here when SOS packets are received from the
                mesh network
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {alerts.map((alert) => (
                <AlertCard key={alert.alertId} alert={alert} />
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
