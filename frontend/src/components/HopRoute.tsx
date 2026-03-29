import { Smartphone, Server, ArrowRight } from "lucide-react";

interface Props {
  route: string[];
  senderId: string;
  relayDeviceId: string;
}

export default function HopRoute({ route, senderId, relayDeviceId }: Props) {
  // Build full chain: originator → relays → server
  const allNodes = route.length > 0 ? [...route] : [senderId];
  // Ensure originator is first
  if (allNodes[0] !== senderId) {
    allNodes.unshift(senderId);
  }

  return (
    <div className="flex items-center gap-1 flex-wrap">
      {allNodes.map((deviceId, index) => {
        const isOriginator = index === 0;
        const isUploader = deviceId === relayDeviceId;

        return (
          <div key={`${deviceId}-${index}`} className="flex items-center gap-1">
            {index > 0 && (
              <ArrowRight className="w-3.5 h-3.5 text-text-muted shrink-0" />
            )}
            <div
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg border text-xs font-mono transition-all
                ${
                  isOriginator
                    ? "bg-sos-red/10 border-sos-red/30 text-sos-red"
                    : isUploader
                      ? "bg-safe-green/10 border-safe-green/30 text-safe-green"
                      : "bg-bg-hover border-border text-text-secondary"
                }`}
            >
              <Smartphone className="w-3 h-3 shrink-0" />
              <span>{deviceId}</span>
              {isOriginator && (
                <span className="text-[10px] opacity-60">SOS</span>
              )}
              {isUploader && (
                <span className="text-[10px] opacity-60">UPLOAD</span>
              )}
            </div>
          </div>
        );
      })}

      {/* Server node */}
      <ArrowRight className="w-3.5 h-3.5 text-text-muted shrink-0" />
      <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg border bg-mesh-teal/10 border-mesh-teal/30 text-mesh-teal text-xs font-mono">
        <Server className="w-3 h-3 shrink-0" />
        <span>SERVER</span>
      </div>
    </div>
  );
}
