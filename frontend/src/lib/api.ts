import type { Alert, AlertsResponse, DashboardStats } from "./types";

const API_BASE = import.meta.env.VITE_API_URL || "https://sosmesh.onrender.com";

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${url}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!res.ok) {
    const error = await res.text();
    throw new Error(`API Error ${res.status}: ${error}`);
  }
  return res.json();
}

export async function getAlerts(params?: {
  status?: string;
  severity?: string;
  category?: string;
  limit?: number;
}): Promise<AlertsResponse> {
  const searchParams = new URLSearchParams();
  if (params?.status) searchParams.set("status", params.status);
  if (params?.severity) searchParams.set("severity", params.severity);
  if (params?.category) searchParams.set("category", params.category);
  if (params?.limit) searchParams.set("limit", params.limit.toString());
  const query = searchParams.toString();
  return fetchJson<AlertsResponse>(`/api/alerts${query ? `?${query}` : ""}`);
}

export async function getAlert(alertId: string): Promise<Alert> {
  return fetchJson<Alert>(`/api/alerts/${alertId}`);
}

export async function getStats(): Promise<DashboardStats> {
  return fetchJson<DashboardStats>("/api/stats");
}

export async function updateAlertStatus(
  alertId: string,
  status: string
): Promise<void> {
  await fetchJson(`/api/alerts/${alertId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}

export async function deleteAlert(alertId: string): Promise<void> {
  await fetchJson(`/api/alerts/${alertId}`, { method: "DELETE" });
}
