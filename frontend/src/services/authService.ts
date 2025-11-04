import api from '../utils/api';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export interface AuthResponse {
  success: boolean;
  data: {
    user: {
      id: string;
      username: string;
      email: string;
      role: string;
    };
    token: string;
    refreshToken: string;
  };
  meta: {
    timestamp: string;
    traceId: string;
    version: string;
  };
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface RefreshTokenResponse {
  success: boolean;
  data: {
    token: string;
    refreshToken: string;
  };
}

export interface SecurityEvent {
  type: 'LOGIN_ATTEMPT' | 'LOGIN_SUCCESS' | 'LOGIN_FAILURE' | 'TOKEN_REFRESH' | 'LOGOUT' | 'SUSPICIOUS_ACTIVITY';
  timestamp: string;
  userAgent: string;
  ipAddress?: string;
  details?: any;
}

class AuthService {
  private refreshPromise: Promise<string> | null = null;
  private loginAttempts: Map<string, { count: number; lastAttempt: number }> = new Map();
  private readonly MAX_LOGIN_ATTEMPTS = 5;
  private readonly LOCKOUT_DURATION = 15 * 60 * 1000; // 15 minutes

  async login(credentials: LoginRequest): Promise<AuthResponse> {
    const userKey = credentials.email.toLowerCase();
    
    // Check for rate limiting
    if (this.isAccountLocked(userKey)) {
      this.logSecurityEvent('SUSPICIOUS_ACTIVITY', {
        reason: 'Account locked due to too many failed attempts',
        email: credentials.email,
      });
      throw new Error('帳號已被暫時鎖定，請稍後再試');
    }

    try {
      this.logSecurityEvent('LOGIN_ATTEMPT', { email: credentials.email });
      
      const response = await api.post<AuthResponse>('/auth/login', credentials);
      
      // Reset login attempts on successful login
      this.loginAttempts.delete(userKey);
      
      this.logSecurityEvent('LOGIN_SUCCESS', { 
        email: credentials.email,
        role: response.data.data.user.role 
      });
      
      return response.data;
    } catch (error: any) {
      // Track failed login attempts
      this.trackFailedLogin(userKey);
      
      this.logSecurityEvent('LOGIN_FAILURE', {
        email: credentials.email,
        error: error.response?.data?.error?.message || 'Unknown error',
        statusCode: error.response?.status,
      });
      
      const errorMessage = error.response?.data?.error?.message || '登入失敗';
      
      // Enhanced error messages based on status codes
      if (error.response?.status === 401) {
        throw new Error('電子郵件或密碼錯誤');
      } else if (error.response?.status === 403) {
        throw new Error('帳號已被停用或權限不足');
      } else if (error.response?.status === 429) {
        throw new Error('登入嘗試過於頻繁，請稍後再試');
      }
      
      throw new Error(errorMessage);
    }
  }

  async register(userData: RegisterRequest): Promise<AuthResponse> {
    try {
      this.logSecurityEvent('LOGIN_ATTEMPT', { 
        type: 'REGISTRATION',
        email: userData.email,
        username: userData.username 
      });
      
      const response = await api.post<AuthResponse>('/auth/register', userData);
      
      this.logSecurityEvent('LOGIN_SUCCESS', { 
        type: 'REGISTRATION',
        email: userData.email,
        username: userData.username,
        role: response.data.data.user.role 
      });
      
      return response.data;
    } catch (error: any) {
      this.logSecurityEvent('LOGIN_FAILURE', {
        type: 'REGISTRATION',
        email: userData.email,
        username: userData.username,
        error: error.response?.data?.error?.message || 'Unknown error',
        statusCode: error.response?.status,
      });
      
      const errorMessage = error.response?.data?.error?.message || '註冊失敗';
      
      // Enhanced error messages based on status codes
      if (error.response?.status === 409) {
        throw new Error('電子郵件或使用者名稱已被使用');
      } else if (error.response?.status === 400) {
        throw new Error('註冊資料格式不正確');
      }
      
      throw new Error(errorMessage);
    }
  }

  async refreshToken(refreshToken: string): Promise<string> {
    // Prevent multiple simultaneous refresh requests
    if (this.refreshPromise) {
      return this.refreshPromise;
    }

    this.refreshPromise = this.performRefresh(refreshToken);
    
    try {
      const newToken = await this.refreshPromise;
      return newToken;
    } finally {
      this.refreshPromise = null;
    }
  }

  private async performRefresh(refreshToken: string): Promise<string> {
    try {
      this.logSecurityEvent('TOKEN_REFRESH', { action: 'ATTEMPT' });
      
      const response = await api.post<RefreshTokenResponse>('/auth/refresh', {
        refreshToken,
      });
      
      const { token, refreshToken: newRefreshToken } = response.data.data;
      
      // Update stored tokens
      localStorage.setItem('token', token);
      localStorage.setItem('refreshToken', newRefreshToken);
      
      this.logSecurityEvent('TOKEN_REFRESH', { 
        action: 'SUCCESS',
        newTokenExpiry: this.getTokenExpirationTime(token)
      });
      
      return token;
    } catch (error: any) {
      // Clear invalid tokens
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      
      this.logSecurityEvent('TOKEN_REFRESH', { 
        action: 'FAILURE',
        error: error.response?.data?.error?.message || 'Unknown error',
        statusCode: error.response?.status,
      });
      
      throw new Error('Token refresh failed');
    }
  }

  async logout(): Promise<void> {
    try {
      const refreshToken = localStorage.getItem('refreshToken');
      
      this.logSecurityEvent('LOGOUT', { 
        action: 'ATTEMPT',
        hasRefreshToken: !!refreshToken 
      });
      
      if (refreshToken) {
        await api.post('/auth/logout', { refreshToken });
      }
      
      this.logSecurityEvent('LOGOUT', { action: 'SUCCESS' });
    } catch (error) {
      // Ignore logout errors, still clear local storage
      console.warn('Logout request failed:', error);
      this.logSecurityEvent('LOGOUT', { 
        action: 'ERROR',
        error: (error as any).message 
      });
    } finally {
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
    }
  }

  isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const currentTime = Date.now() / 1000;
      return payload.exp < currentTime;
    } catch (error) {
      return true;
    }
  }

  getTokenExpirationTime(token: string): number | null {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000; // Convert to milliseconds
    } catch (error) {
      return null;
    }
  }

  getUserFromToken(token: string): any {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return {
        id: payload.sub,
        username: payload.username,
        email: payload.email,
        role: payload.role,
      };
    } catch (error) {
      return null;
    }
  }

  private trackFailedLogin(userKey: string): void {
    const now = Date.now();
    const attempts = this.loginAttempts.get(userKey) || { count: 0, lastAttempt: 0 };
    
    // Reset count if last attempt was more than lockout duration ago
    if (now - attempts.lastAttempt > this.LOCKOUT_DURATION) {
      attempts.count = 0;
    }
    
    attempts.count++;
    attempts.lastAttempt = now;
    this.loginAttempts.set(userKey, attempts);
  }

  private isAccountLocked(userKey: string): boolean {
    const attempts = this.loginAttempts.get(userKey);
    if (!attempts) return false;
    
    const now = Date.now();
    const timeSinceLastAttempt = now - attempts.lastAttempt;
    
    return attempts.count >= this.MAX_LOGIN_ATTEMPTS && timeSinceLastAttempt < this.LOCKOUT_DURATION;
  }

  private logSecurityEvent(type: SecurityEvent['type'], details?: any): void {
    const event: SecurityEvent = {
      type,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      details,
    };

    // Log to console in development
    if (process.env.NODE_ENV === 'development') {
      console.log('Security Event:', event);
    }

    // In production, this would send to a security monitoring service
    // For now, we'll store in sessionStorage for debugging
    try {
      const existingEvents = JSON.parse(sessionStorage.getItem('securityEvents') || '[]');
      existingEvents.push(event);
      
      // Keep only last 50 events to prevent storage overflow
      if (existingEvents.length > 50) {
        existingEvents.splice(0, existingEvents.length - 50);
      }
      
      sessionStorage.setItem('securityEvents', JSON.stringify(existingEvents));
    } catch (error) {
      console.warn('Failed to log security event:', error);
    }
  }

  // Method to get security events (for debugging/monitoring)
  getSecurityEvents(): SecurityEvent[] {
    try {
      return JSON.parse(sessionStorage.getItem('securityEvents') || '[]');
    } catch (error) {
      return [];
    }
  }

  // Method to clear security events
  clearSecurityEvents(): void {
    sessionStorage.removeItem('securityEvents');
  }
}

export const authService = new AuthService();
export default authService;