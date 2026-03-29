interface Props {
  status: string;
  size?: "sm" | "md";
}

const statusConfig: Record<string, { bg: string; text: string; dot: string }> = {
  ACTIVE: {
    bg: "bg-sos-red/15",
    text: "text-sos-red",
    dot: "bg-sos-red",
  },
  RESPONDING: {
    bg: "bg-warn-amber/15",
    text: "text-warn-amber",
    dot: "bg-warn-amber",
  },
  RESOLVED: {
    bg: "bg-safe-green/15",
    text: "text-safe-green",
    dot: "bg-safe-green",
  },
};

export default function StatusBadge({ status, size = "sm" }: Props) {
  const config = statusConfig[status] ?? statusConfig.ACTIVE;
  const padding = size === "sm" ? "px-2 py-0.5" : "px-3 py-1";
  const textSize = size === "sm" ? "text-xs" : "text-sm";

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full font-medium ${config.bg} ${config.text} ${padding} ${textSize}`}
    >
      <span
        className={`w-1.5 h-1.5 rounded-full ${config.dot} ${status === "ACTIVE" ? "animate-pulse" : ""}`}
      />
      {status}
    </span>
  );
}
