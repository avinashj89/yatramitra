import type { ReactNode } from "react";
import { Box, Chip, Link, Typography } from "@mui/material";
import SosIcon from "@mui/icons-material/Sos";
import { SOS_LABELS } from "../lib/parse";
import type { ChatMessage } from "../types";

/** One chat message; an SOS is shown in red with its kind and a link to where it was sent from. */
export function ChatMessageBox({ message, meta, action }: { message: ChatMessage; meta: ReactNode; action?: ReactNode }) {
  const sos = message.sos;
  return (
    <Box
      className="flex items-start gap-3 rounded-xl px-4 py-3"
      sx={sos ? { bgcolor: "#C62828", color: "#fff" } : { bgcolor: "action.hover" }}
    >
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          {sos && <Chip size="small" icon={<SosIcon sx={{ color: "#C62828 !important" }} />} label={SOS_LABELS[sos.type]} sx={{ bgcolor: "#fff", color: "#C62828", fontWeight: 700 }} />}
          <Typography variant="subtitle2">{message.authorName}</Typography>
          <Typography variant="caption" sx={{ color: sos ? "rgba(255,255,255,0.85)" : "text.secondary" }}>
            {meta}
          </Typography>
        </div>
        <Typography variant="body2" className="mt-0.5 whitespace-pre-wrap break-words">
          {message.text}
        </Typography>
        {sos && sos.lat !== null && sos.lng !== null && (
          <Link
            href={`https://www.google.com/maps/search/?api=1&query=${sos.lat},${sos.lng}`}
            target="_blank"
            rel="noopener"
            sx={{ color: "#fff", fontWeight: 600 }}
            className="mt-1 inline-block"
          >
            Open location{sos.locationName ? ` (${sos.locationName})` : ""}
          </Link>
        )}
      </div>
      {action}
    </Box>
  );
}
