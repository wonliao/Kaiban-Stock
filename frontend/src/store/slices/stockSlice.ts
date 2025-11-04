import { createSlice, PayloadAction } from '@reduxjs/toolkit';

export interface StockSnapshot {
  code: string;
  name: string;
  currentPrice: number;
  changePercent: number;
  volume: number;
  ma5?: number;
  ma10?: number;
  ma20?: number;
  ma60?: number;
  rsi?: number;
  kdK?: number;
  kdD?: number;
  updatedAt: string;
  dataSource: string;
  delayMinutes: number;
}

interface StockState {
  snapshots: Record<string, StockSnapshot>;
  loading: boolean;
  error: string | null;
}

const initialState: StockState = {
  snapshots: {},
  loading: false,
  error: null,
};

const stockSlice = createSlice({
  name: 'stock',
  initialState,
  reducers: {
    fetchSnapshotStart: (state) => {
      state.loading = true;
      state.error = null;
    },
    fetchSnapshotSuccess: (state, action: PayloadAction<StockSnapshot>) => {
      state.loading = false;
      state.snapshots[action.payload.code] = action.payload;
      state.error = null;
    },
    fetchSnapshotFailure: (state, action: PayloadAction<string>) => {
      state.loading = false;
      state.error = action.payload;
    },
    updateSnapshot: (state, action: PayloadAction<StockSnapshot>) => {
      state.snapshots[action.payload.code] = action.payload;
    },
    clearError: (state) => {
      state.error = null;
    },
  },
});

export const {
  fetchSnapshotStart,
  fetchSnapshotSuccess,
  fetchSnapshotFailure,
  updateSnapshot,
  clearError,
} = stockSlice.actions;

export default stockSlice.reducer;