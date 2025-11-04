import axios from 'axios';
import { store } from '../store';
import { refreshTokenAsync, logoutAsync } from '../store/slices/authSlice';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8081';

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add auth token
api.interceptors.request.use(
  (config) => {
    const state = store.getState();
    const token = state.auth.token;
    
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor to handle token refresh and enhanced error handling
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Handle 401 Unauthorized - Token expired or invalid
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      const state = store.getState();
      const { refreshToken, tokenRefreshing } = state.auth;

      // Avoid multiple simultaneous refresh attempts
      if (tokenRefreshing) {
        // Wait for ongoing refresh to complete
        return new Promise((resolve, reject) => {
          const checkRefresh = () => {
            const currentState = store.getState();
            if (!currentState.auth.tokenRefreshing) {
              if (currentState.auth.token) {
                // Retry with new token
                originalRequest.headers.Authorization = `Bearer ${currentState.auth.token}`;
                resolve(api(originalRequest));
              } else {
                reject(error);
              }
            } else {
              setTimeout(checkRefresh, 100);
            }
          };
          checkRefresh();
        });
      }

      if (refreshToken) {
        try {
          await store.dispatch(refreshTokenAsync()).unwrap();
          
          // Get the new token and retry the request
          const newState = store.getState();
          if (newState.auth.token) {
            originalRequest.headers.Authorization = `Bearer ${newState.auth.token}`;
            return api(originalRequest);
          }
        } catch (refreshError) {
          // Refresh failed, logout user
          store.dispatch(logoutAsync());
          return Promise.reject(error);
        }
      } else {
        // No refresh token, logout user
        store.dispatch(logoutAsync());
      }
    }

    // Handle 403 Forbidden - Enhanced error details
    if (error.response?.status === 403) {
      const errorData = error.response.data;
      const enhancedError = {
        ...error,
        response: {
          ...error.response,
          data: {
            ...errorData,
            error: {
              ...errorData.error,
              code: errorData.error?.code || 'FORBIDDEN',
              message: errorData.error?.message || '您沒有權限執行此操作',
              hint: errorData.error?.hint || '請聯繫管理員或檢查您的權限設定',
              traceId: errorData.error?.traceId || `trace-${Date.now()}`,
              timestamp: errorData.error?.timestamp || new Date().toISOString(),
            }
          }
        }
      };
      
      // Log security event for 403 errors
      console.warn('Access denied:', {
        url: originalRequest.url,
        method: originalRequest.method,
        status: 403,
        traceId: enhancedError.response.data.error.traceId,
      });
      
      return Promise.reject(enhancedError);
    }

    // Handle other HTTP errors with enhanced details
    if (error.response) {
      const errorData = error.response.data;
      if (!errorData.error?.traceId) {
        error.response.data = {
          ...errorData,
          error: {
            ...errorData.error,
            traceId: `trace-${Date.now()}`,
            timestamp: new Date().toISOString(),
          }
        };
      }
    }

    return Promise.reject(error);
  }
);

export default api;