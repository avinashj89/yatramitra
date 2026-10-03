import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import type { TripSummary } from "../types";
import { emptyData, type AdminActions, type AdminData, type DataSource, type TripDetail } from "./source";

const SourceContext = createContext<DataSource | null>(null);
const DataContext = createContext<AdminData>(emptyData);

/** Folds a partial update into the current data; an error set to undefined clears it. */
export function mergeData(prev: AdminData, patch: Partial<AdminData>): AdminData {
  const next: AdminData = {
    ...prev,
    ...patch,
    ready: { ...prev.ready, ...(patch.ready ?? {}) },
    errors: { ...prev.errors },
  };
  if (patch.errors) {
    for (const [key, message] of Object.entries(patch.errors)) {
      if (message === undefined) delete next.errors[key as keyof AdminData["errors"]];
      else next.errors[key as keyof AdminData["errors"]] = message;
    }
  }
  return next;
}

/** Keeps the whole app's data live while an admin is signed in. */
export function DataProvider({ source, children }: { source: DataSource; children: ReactNode }) {
  const [data, setData] = useState<AdminData>(emptyData);
  useEffect(() => source.subscribeAll((patch) => setData((prev) => mergeData(prev, patch))), [source]);
  return (
    <SourceContext.Provider value={source}>
      <DataContext.Provider value={data}>{children}</DataContext.Provider>
    </SourceContext.Provider>
  );
}

function useSource(): DataSource {
  const source = useContext(SourceContext);
  if (!source) throw new Error("Data hooks must be used inside DataProvider");
  return source;
}

export const useAdminData = () => useContext(DataContext);
export const useActions = (): AdminActions => useSource().actions;
export const useDataMode = () => useSource().mode;

export function useTripDetail(code: string | undefined): TripDetail {
  const source = useSource();
  const [detail, setDetail] = useState<TripDetail>({ itinerary: [], chat: [], ready: false });
  useEffect(() => {
    if (!code) return;
    setDetail({ itinerary: [], chat: [], ready: false });
    return source.subscribeTrip(code, setDetail);
  }, [source, code]);
  return detail;
}

export function useUserTrips(uid: string | null): { trips: TripSummary[]; error?: string; ready: boolean } {
  const source = useSource();
  const [state, setState] = useState<{ trips: TripSummary[]; error?: string; ready: boolean }>({ trips: [], ready: false });
  useEffect(() => {
    if (!uid) return;
    setState({ trips: [], ready: false });
    return source.subscribeUserTrips(uid, (trips, error) => setState({ trips, error, ready: true }));
  }, [source, uid]);
  return state;
}
