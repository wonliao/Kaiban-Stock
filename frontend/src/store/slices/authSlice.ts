import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import authService, { LoginRequest, RegisterRequest } from '../../services/authService';

interface User {
  id: string;
  email: string;
  username: string;
  role: string;
}

interface AuthState {
  user: User | null;
  token: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
  tokenRefreshing: boolean;
}

// Initialize state from localStorage
const token = localStorage.getItem('token');
const refreshToken = localStorage.getItem('refreshToken');
let user: User | null = null;
let isAuthenticated = false;

if (token && !authService.isTokenExpired(token)) {
  user = authService.getUserFromToken(token);
  isAuthenticated = true;
} else if (token) {
  // Token expired, clear it
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
}

const initialState: AuthState = {
  user,
  token: isAuthenticated ? token : null,
  refreshToken: isAuthenticated ? refreshToken : null,
  isAuthenticated,
  loading: false,
  error: null,
  tokenRefreshing: false,
};

// Async thunks
export const loginAsync = createAsyncThunk(
  'auth/login',
  async (credentials: LoginRequest, { rejectWithValue }) => {
    try {
      const response = await authService.login(credentials);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);

export const registerAsync = createAsyncThunk(
  'auth/register',
  async (userData: RegisterRequest, { rejectWithValue }) => {
    try {
      const response = await authService.register(userData);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);

export const refreshTokenAsync = createAsyncThunk(
  'auth/refreshToken',
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as { auth: AuthState };
      const refreshToken = state.auth.refreshToken;
      
      if (!refreshToken) {
        throw new Error('No refresh token available');
      }
      
      const newToken = await authService.refreshToken(refreshToken);
      const user = authService.getUserFromToken(newToken);
      
      return { token: newToken, user };
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);

export const logoutAsync = createAsyncThunk(
  'auth/logout',
  async (_, { rejectWithValue }) => {
    try {
      await authService.logout();
    } catch (error: any) {
      // Don't reject on logout errors, still clear state
      console.warn('Logout error:', error.message);
    }
  }
);

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    setTokenRefreshing: (state, action: PayloadAction<boolean>) => {
      state.tokenRefreshing = action.payload;
    },
  },
  extraReducers: (builder) => {
    // Login
    builder
      .addCase(loginAsync.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(loginAsync.fulfilled, (state, action) => {
        state.loading = false;
        state.user = action.payload.user;
        state.token = action.payload.token;
        state.refreshToken = action.payload.refreshToken;
        state.isAuthenticated = true;
        state.error = null;
        
        // Store in localStorage
        localStorage.setItem('token', action.payload.token);
        localStorage.setItem('refreshToken', action.payload.refreshToken);
      })
      .addCase(loginAsync.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
        state.isAuthenticated = false;
      });

    // Register
    builder
      .addCase(registerAsync.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(registerAsync.fulfilled, (state, action) => {
        state.loading = false;
        state.user = action.payload.user;
        state.token = action.payload.token;
        state.refreshToken = action.payload.refreshToken;
        state.isAuthenticated = true;
        state.error = null;
        
        // Store in localStorage
        localStorage.setItem('token', action.payload.token);
        localStorage.setItem('refreshToken', action.payload.refreshToken);
      })
      .addCase(registerAsync.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
        state.isAuthenticated = false;
      });

    // Refresh Token
    builder
      .addCase(refreshTokenAsync.pending, (state) => {
        state.tokenRefreshing = true;
      })
      .addCase(refreshTokenAsync.fulfilled, (state, action) => {
        state.tokenRefreshing = false;
        state.token = action.payload.token;
        state.user = action.payload.user;
        state.isAuthenticated = true;
        state.error = null;
      })
      .addCase(refreshTokenAsync.rejected, (state) => {
        state.tokenRefreshing = false;
        state.user = null;
        state.token = null;
        state.refreshToken = null;
        state.isAuthenticated = false;
        
        // Clear localStorage
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
      });

    // Logout
    builder
      .addCase(logoutAsync.fulfilled, (state) => {
        state.user = null;
        state.token = null;
        state.refreshToken = null;
        state.isAuthenticated = false;
        state.error = null;
        state.loading = false;
        state.tokenRefreshing = false;
        
        // Clear localStorage
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
      });
  },
});

export const {
  clearError,
  setTokenRefreshing,
} = authSlice.actions;

export default authSlice.reducer;