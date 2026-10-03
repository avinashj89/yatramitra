import { useState, type ReactNode } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  Skeleton,
  Tooltip,
  Typography,
} from "@mui/material";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import CheckIcon from "@mui/icons-material/Check";
import type { Trip } from "../types";

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <Typography variant="h4" component="h1">
          {title}
        </Typography>
        {subtitle && (
          <Typography variant="body2" color="text.secondary" className="mt-1" component="div">
            {subtitle}
          </Typography>
        )}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function KpiCard({
  label,
  value,
  hint,
  icon,
  loading,
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  icon: ReactNode;
  loading?: boolean;
}) {
  return (
    <Card className="h-full">
      <CardContent className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <Typography variant="body2" color="text.secondary" fontWeight={500}>
            {label}
          </Typography>
          {loading ? (
            <Skeleton width={90} height={44} />
          ) : (
            <Typography variant="h4" component="p" className="mt-1">
              {value}
            </Typography>
          )}
          {hint && (
            <Typography variant="caption" color="text.secondary" component="p" className="mt-1">
              {hint}
            </Typography>
          )}
        </div>
        <Box
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
          sx={{ bgcolor: "action.hover", color: "primary.main" }}
          aria-hidden
        >
          {icon}
        </Box>
      </CardContent>
    </Card>
  );
}

/** Planning (not started) / Started / Completed, the way the app describes a trip. */
export function tripStage(trip: Pick<Trip, "status" | "startedAt">): "Planning" | "Started" | "Completed" {
  if (trip.status === "COMPLETED") return "Completed";
  return trip.startedAt > 0 ? "Started" : "Planning";
}

export function TripStatusChip({ trip, size = "small" }: { trip: Pick<Trip, "status" | "startedAt">; size?: "small" | "medium" }) {
  const stage = tripStage(trip);
  const color = stage === "Completed" ? "default" : stage === "Started" ? "success" : "info";
  return <Chip size={size} label={stage} color={color} variant={stage === "Completed" ? "outlined" : "filled"} />;
}

export function ErrorBanner({ errors }: { errors: (string | undefined)[] }) {
  const unique = [...new Set(errors.filter((e): e is string => Boolean(e)))];
  if (unique.length === 0) return null;
  return (
    <div className="mb-4 flex flex-col gap-2">
      {unique.map((message) => (
        <Alert key={message} severity="warning" variant="outlined">
          {message}
        </Alert>
      ))}
    </div>
  );
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  danger,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  onConfirm: () => Promise<void> | void;
  onClose: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText component="div">{message}</DialogContentText>
        {error && (
          <Alert severity="error" className="mt-3">
            {error}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button
          variant="contained"
          color={danger ? "error" : "primary"}
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              await onConfirm();
              onClose();
            } catch (e) {
              setError((e as Error).message || "That didn't work. Please try again.");
            } finally {
              setBusy(false);
            }
          }}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function CopyButton({ value, label = "Copy" }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <Tooltip title={copied ? "Copied" : label}>
      <IconButton
        size="small"
        aria-label={label}
        onClick={async () => {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        }}
      >
        {copied ? <CheckIcon fontSize="small" /> : <ContentCopyIcon fontSize="small" />}
      </IconButton>
    </Tooltip>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <Typography variant="subtitle1" fontWeight={600}>
        {title}
      </Typography>
      {children && (
        <Typography variant="body2" color="text.secondary" className="mt-1 max-w-md">
          {children}
        </Typography>
      )}
    </div>
  );
}

export function SectionCard({ title, action, children, className }: { title: string; action?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <Card className={className}>
      <div className="flex items-center justify-between gap-2 px-5 pt-4">
        <Typography variant="h6" component="h2">
          {title}
        </Typography>
        {action}
      </div>
      <CardContent className="pt-3">{children}</CardContent>
    </Card>
  );
}
