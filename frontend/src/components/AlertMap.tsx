import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import type { LocationInfo, RoutePoint } from "../lib/types";

interface Props {
  location: LocationInfo | null;
  route?: Array<RoutePoint | string>;
  senderId?: string;
  relayLocation?: LocationInfo | null;
  className?: string;
}

export default function AlertMap({
  location,
  route = [],
  senderId,
  relayLocation,
  className = "",
}: Props) {
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);

  useEffect(() => {
    if (!mapRef.current || !location) return;

    // Cleanup previous map
    if (mapInstanceRef.current) {
      mapInstanceRef.current.remove();
      mapInstanceRef.current = null;
    }

    const map = L.map(mapRef.current, {
      zoomControl: false,
      attributionControl: true,
    }).setView([location.lat, location.lng], 15);

    L.tileLayer("https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png", {
      attribution: '&copy; <a href="https://carto.com">CARTO</a>',
      maxZoom: 19,
    }).addTo(map);

    // SOS origin marker (red)
    const sosIcon = L.divIcon({
      html: `<div style="
        width: 16px; height: 16px;
        background: #F85149;
        border: 2px solid #fff;
        border-radius: 50%;
        box-shadow: 0 0 12px rgba(248,81,73,0.6);
      "></div>`,
      iconSize: [16, 16],
      iconAnchor: [8, 8],
      className: "",
    });

    L.marker([location.lat, location.lng], { icon: sosIcon })
      .addTo(map)
      .bindPopup(
        `<div style="color:#333;font-family:monospace;font-size:12px">
          <b>SOS Origin</b><br>
          ${location.lat.toFixed(6)}, ${location.lng.toFixed(6)}<br>
          Accuracy: ${location.accuracy?.toFixed(0) ?? "—"}m
          ${location.address ? `<br>${location.address}` : ""}
        </div>`
      );

    // Accuracy circle
    L.circle([location.lat, location.lng], {
      radius: location.accuracy || 50,
      color: "#F85149",
      fillColor: "#F85149",
      fillOpacity: 0.08,
      weight: 1,
    }).addTo(map);

    const routePoints = route
      .map((point) => (typeof point === "string" ? null : point))
      .filter((point): point is RoutePoint => !!point && !!point.location);

    if (routePoints.length > 0) {
      const meshPath: [number, number][] = routePoints.map((point) => [
        point.location!.lat,
        point.location!.lng,
      ]);

      // Ensure origin exists as first map point.
      if (meshPath.length === 0 || meshPath[0][0] !== location.lat || meshPath[0][1] !== location.lng) {
        meshPath.unshift([location.lat, location.lng]);
      }

      L.polyline(meshPath, {
        color: "#58A6FF",
        weight: 3,
        opacity: 0.85,
      }).addTo(map);

      routePoints.forEach((point, index) => {
        const isOrigin = point.deviceId === senderId || index === 0;
        const markerColor = isOrigin ? "#F85149" : "#58A6FF";
        const icon = L.divIcon({
          html: `<div style="
            width: 12px; height: 12px;
            background: ${markerColor};
            border: 2px solid #fff;
            border-radius: 50%;
            box-shadow: 0 0 8px ${isOrigin ? "rgba(248,81,73,0.55)" : "rgba(88,166,255,0.45)"};
          "></div>`,
          iconSize: [12, 12],
          iconAnchor: [6, 6],
          className: "",
        });

        L.marker([point.location!.lat, point.location!.lng], { icon })
          .addTo(map)
          .bindPopup(
            `<div style="color:#333;font-family:monospace;font-size:12px">
              <b>${isOrigin ? "Origin Device" : "Relay Device"}</b><br>
              ${point.deviceId}<br>
              ${point.location!.lat.toFixed(6)}, ${point.location!.lng.toFixed(6)}
            </div>`
          );
      });

      map.fitBounds(L.latLngBounds(meshPath), { padding: [40, 40] });
    } else if (relayLocation) {
      const relayIcon = L.divIcon({
        html: `<div style="
          width: 12px; height: 12px;
          background: #3FB950;
          border: 2px solid #fff;
          border-radius: 50%;
          box-shadow: 0 0 8px rgba(63,185,80,0.5);
        "></div>`,
        iconSize: [12, 12],
        iconAnchor: [6, 6],
        className: "",
      });

      L.marker([relayLocation.lat, relayLocation.lng], { icon: relayIcon })
        .addTo(map)
        .bindPopup(
          `<div style="color:#333;font-family:monospace;font-size:12px">
            <b>Relay Device</b><br>
            ${relayLocation.lat.toFixed(6)}, ${relayLocation.lng.toFixed(6)}
          </div>`
        );

      L.polyline(
        [
          [location.lat, location.lng],
          [relayLocation.lat, relayLocation.lng],
        ],
        { color: "#58A6FF", weight: 2, dashArray: "6, 4", opacity: 0.7 }
      ).addTo(map);

      const bounds = L.latLngBounds(
        [location.lat, location.lng],
        [relayLocation.lat, relayLocation.lng]
      );
      map.fitBounds(bounds, { padding: [40, 40] });
    }

    L.control.zoom({ position: "bottomright" }).addTo(map);

    mapInstanceRef.current = map;

    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, [location, relayLocation, route, senderId]);

  if (!location) {
    return (
      <div
        className={`bg-bg-card border border-border rounded-xl flex items-center justify-center text-text-muted text-sm ${className}`}
      >
        No location data available
      </div>
    );
  }

  return (
    <div
      ref={mapRef}
      className={`rounded-xl overflow-hidden border border-border ${className}`}
    />
  );
}
