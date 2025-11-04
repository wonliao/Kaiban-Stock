import { createSlice, PayloadAction } from '@reduxjs/toolkit';

export interface Card {
  id: string;
  stockCode: string;
  stockName: string;
  status: 'watch' | 'readyToBuy' | 'hold' | 'sell' | 'alerts';
  note?: string;
  currentPrice?: number;
  changePercent?: number;
  volume?: number;
  ma20?: number;
  rsi?: number;
  createdAt: string;
  updatedAt: string;
}

interface KanbanState {
  cards: Card[];
  loading: boolean;
  error: string | null;
  searchQuery: string;
  statusFilter: string | null;
  sortBy: 'updatedAt' | 'changePercent' | 'volume';
  sortOrder: 'asc' | 'desc';
}

const initialState: KanbanState = {
  cards: [],
  loading: false,
  error: null,
  searchQuery: '',
  statusFilter: null,
  sortBy: 'updatedAt',
  sortOrder: 'desc',
};

const kanbanSlice = createSlice({
  name: 'kanban',
  initialState,
  reducers: {
    fetchCardsStart: (state) => {
      state.loading = true;
      state.error = null;
    },
    fetchCardsSuccess: (state, action: PayloadAction<Card[]>) => {
      state.loading = false;
      state.cards = action.payload;
      state.error = null;
    },
    fetchCardsFailure: (state, action: PayloadAction<string>) => {
      state.loading = false;
      state.error = action.payload;
    },
    updateCardStatus: (state, action: PayloadAction<{ cardId: string; status: Card['status'] }>) => {
      const card = state.cards.find(c => c.id === action.payload.cardId);
      if (card) {
        card.status = action.payload.status;
        card.updatedAt = new Date().toISOString();
      }
    },
    addCard: (state, action: PayloadAction<Card>) => {
      state.cards.push(action.payload);
    },
    removeCard: (state, action: PayloadAction<string>) => {
      state.cards = state.cards.filter(card => card.id !== action.payload);
    },
    setSearchQuery: (state, action: PayloadAction<string>) => {
      state.searchQuery = action.payload;
    },
    setStatusFilter: (state, action: PayloadAction<string | null>) => {
      state.statusFilter = action.payload;
    },
    setSorting: (state, action: PayloadAction<{ sortBy: KanbanState['sortBy']; sortOrder: KanbanState['sortOrder'] }>) => {
      state.sortBy = action.payload.sortBy;
      state.sortOrder = action.payload.sortOrder;
    },
    clearError: (state) => {
      state.error = null;
    },
  },
});

export const {
  fetchCardsStart,
  fetchCardsSuccess,
  fetchCardsFailure,
  updateCardStatus,
  addCard,
  removeCard,
  setSearchQuery,
  setStatusFilter,
  setSorting,
  clearError,
} = kanbanSlice.actions;

export default kanbanSlice.reducer;