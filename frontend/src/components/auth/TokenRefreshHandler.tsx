import { useEffect, useRef } from 'react';
import { useAppDispatch } from '../../hooks/useAppDispatch';
import { useAppSelector } from '../../hooks/useAppSelector';
import { refreshTokenAsync, logoutAsync } from '../../store/slices/authSlice';
import authService from '../../services/authService';

// Session timeout warning (5 minutes before token expiry)
const SESSION_WARNING_TIME = 5 * 60 * 1000;

const TokenRefreshHandler: React.FC = () => {
  const dispatch = useAppDispatch();
  const { token, refreshToken, isAuthenticated } = useAppSelector((state) => state.auth);
  const refreshTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const warningTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const lastActivityRef = useRef<number>(Date.now());

  // Track user activity for session management
  useEffect(() => {
    const updateActivity = () => {
      lastActivityRef.current = Date.now();
    };

    const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];
    events.forEach(event => {
      document.addEventListener(event, updateActivity, true);
    });

    return () => {
      events.forEach(event => {
        document.removeEventListener(event, updateActivity, true);
      });
    };
  }, []);

  useEffect(() => {
    if (!isAuthenticated || !token || !refreshToken) {
      return;
    }

    const scheduleTokenRefresh = () => {
      const expirationTime = authService.getTokenExpirationTime(token);
      
      if (!expirationTime) {
        // Invalid token, logout
        dispatch(logoutAsync());
        return;
      }

      const currentTime = Date.now();
      const timeUntilExpiry = expirationTime - currentTime;
      
      // Schedule session warning
      const warningTime = Math.max(0, timeUntilExpiry - SESSION_WARNING_TIME);
      if (warningTimeoutRef.current) {
        clearTimeout(warningTimeoutRef.current);
      }
      
      warningTimeoutRef.current = setTimeout(() => {
        // Check if user has been active recently
        const timeSinceActivity = Date.now() - lastActivityRef.current;
        const inactivityThreshold = 30 * 60 * 1000; // 30 minutes
        
        if (timeSinceActivity > inactivityThreshold) {
          // User has been inactive, show warning or auto-logout
          console.warn('User inactive for extended period, session will expire soon');
          // In a real app, you might show a modal warning here
        }
      }, warningTime);
      
      // Refresh token 5 minutes before expiry (or immediately if already expired)
      const refreshTime = Math.max(0, timeUntilExpiry - 5 * 60 * 1000);

      if (refreshTimeoutRef.current) {
        clearTimeout(refreshTimeoutRef.current);
      }

      refreshTimeoutRef.current = setTimeout(async () => {
        try {
          // Check if user has been active recently before refreshing
          const timeSinceActivity = Date.now() - lastActivityRef.current;
          const maxInactivity = 60 * 60 * 1000; // 1 hour
          
          if (timeSinceActivity > maxInactivity) {
            // User has been inactive too long, logout instead of refresh
            console.log('Auto-logout due to inactivity');
            dispatch(logoutAsync());
            return;
          }
          
          await dispatch(refreshTokenAsync()).unwrap();
        } catch (error) {
          console.error('Token refresh failed:', error);
          // The slice will handle logout on refresh failure
        }
      }, refreshTime);
    };

    scheduleTokenRefresh();

    // Cleanup timeouts on unmount or dependency change
    return () => {
      if (refreshTimeoutRef.current) {
        clearTimeout(refreshTimeoutRef.current);
      }
      if (warningTimeoutRef.current) {
        clearTimeout(warningTimeoutRef.current);
      }
    };
  }, [token, refreshToken, isAuthenticated, dispatch]);

  // Handle page visibility change to refresh token when page becomes visible
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && isAuthenticated && token) {
        // Check if token is expired or will expire soon
        const expirationTime = authService.getTokenExpirationTime(token);
        if (expirationTime) {
          const currentTime = Date.now();
          const timeUntilExpiry = expirationTime - currentTime;
          
          // If token expires in less than 10 minutes, refresh it
          if (timeUntilExpiry < 10 * 60 * 1000) {
            dispatch(refreshTokenAsync());
          }
        }
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [isAuthenticated, token, dispatch]);

  // Handle storage events (for multi-tab synchronization)
  useEffect(() => {
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === 'token' && !e.newValue && isAuthenticated) {
        // Token was removed in another tab, logout this tab too
        dispatch(logoutAsync());
      }
    };

    window.addEventListener('storage', handleStorageChange);
    
    return () => {
      window.removeEventListener('storage', handleStorageChange);
    };
  }, [isAuthenticated, dispatch]);

  return null; // This component doesn't render anything
};

export default TokenRefreshHandler;