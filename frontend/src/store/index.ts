import { configureStore } from '@reduxjs/toolkit';
import authSlice from './slices/authSlice';
import kanbanSlice from './slices/kanbanSlice';
import stockSlice from './slices/stockSlice';
import uiSlice from './slices/uiSlice';

export const store = configureStore({
  reducer: {
    auth: authSlice,
    kanban: kanbanSlice,
    stock: stockSlice,
    ui: uiSlice,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredActions: ['persist/PERSIST'],
      },
    }),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;