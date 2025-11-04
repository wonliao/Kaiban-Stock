import authService from '../authService';

// Mock axios
jest.mock('../../utils/api', () => ({
  post: jest.fn(),
}));

import api from '../../utils/api';
const mockedApi = api as jest.Mocked<typeof api>;

describe('AuthService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
  });

  describe('isTokenExpired', () => {
    it('returns true for expired token', () => {
      // Create a token that expired 1 hour ago
      const expiredTime = Math.floor(Date.now() / 1000) - 3600;
      const payload = { exp: expiredTime };
      const token = `header.${btoa(JSON.stringify(payload))}.signature`;

      expect(authService.isTokenExpired(token)).toBe(true);
    });

    it('returns false for valid token', () => {
      // Create a token that expires in 1 hour
      const futureTime = Math.floor(Date.now() / 1000) + 3600;
      const payload = { exp: futureTime };
      const token = `header.${btoa(JSON.stringify(payload))}.signature`;

      expect(authService.isTokenExpired(token)).toBe(false);
    });

    it('returns true for malformed token', () => {
      expect(authService.isTokenExpired('invalid-token')).toBe(true);
    });
  });

  describe('getUserFromToken', () => {
    it('extracts user data from valid token', () => {
      const userData = {
        sub: '123',
        username: 'testuser',
        email: 'test@example.com',
        role: 'USER',
      };
      const token = `header.${btoa(JSON.stringify(userData))}.signature`;

      const result = authService.getUserFromToken(token);

      expect(result).toEqual({
        id: '123',
        username: 'testuser',
        email: 'test@example.com',
        role: 'USER',
      });
    });

    it('returns null for malformed token', () => {
      expect(authService.getUserFromToken('invalid-token')).toBeNull();
    });
  });

  describe('getTokenExpirationTime', () => {
    it('returns expiration time in milliseconds', () => {
      const expTime = Math.floor(Date.now() / 1000) + 3600; // 1 hour from now
      const payload = { exp: expTime };
      const token = `header.${btoa(JSON.stringify(payload))}.signature`;

      const result = authService.getTokenExpirationTime(token);

      expect(result).toBe(expTime * 1000);
    });

    it('returns null for malformed token', () => {
      expect(authService.getTokenExpirationTime('invalid-token')).toBeNull();
    });
  });

  describe('login', () => {
    it('successfully logs in user', async () => {
      const mockResponse = {
        data: {
          success: true,
          data: {
            user: {
              id: '123',
              username: 'testuser',
              email: 'test@example.com',
              role: 'USER',
            },
            token: 'access-token',
            refreshToken: 'refresh-token',
          },
        },
      };

      mockedApi.post.mockResolvedValueOnce(mockResponse);

      const credentials = {
        email: 'test@example.com',
        password: 'password123',
      };

      const result = await authService.login(credentials);

      expect(mockedApi.post).toHaveBeenCalledWith('/auth/login', credentials);
      expect(result).toEqual(mockResponse.data);
    });

    it('throws error on login failure', async () => {
      const mockError = {
        response: {
          data: {
            error: {
              message: 'Invalid credentials',
            },
          },
        },
      };

      mockedApi.post.mockRejectedValueOnce(mockError);

      const credentials = {
        email: 'test@example.com',
        password: 'wrongpassword',
      };

      await expect(authService.login(credentials)).rejects.toThrow('Invalid credentials');
    });
  });

  describe('refreshToken', () => {
    it('successfully refreshes token', async () => {
      const mockResponse = {
        data: {
          data: {
            token: 'new-access-token',
            refreshToken: 'new-refresh-token',
          },
        },
      };

      mockedApi.post.mockResolvedValueOnce(mockResponse);

      const result = await authService.refreshToken('old-refresh-token');

      expect(mockedApi.post).toHaveBeenCalledWith('/auth/refresh', {
        refreshToken: 'old-refresh-token',
      });
      expect(result).toBe('new-access-token');
      expect(localStorage.getItem('token')).toBe('new-access-token');
      expect(localStorage.getItem('refreshToken')).toBe('new-refresh-token');
    });

    it('clears tokens on refresh failure', async () => {
      localStorage.setItem('token', 'old-token');
      localStorage.setItem('refreshToken', 'old-refresh-token');

      mockedApi.post.mockRejectedValueOnce(new Error('Refresh failed'));

      await expect(authService.refreshToken('old-refresh-token')).rejects.toThrow('Token refresh failed');

      expect(localStorage.getItem('token')).toBeNull();
      expect(localStorage.getItem('refreshToken')).toBeNull();
    });
  });

  describe('logout', () => {
    it('successfully logs out user', async () => {
      localStorage.setItem('token', 'access-token');
      localStorage.setItem('refreshToken', 'refresh-token');

      mockedApi.post.mockResolvedValueOnce({});

      await authService.logout();

      expect(mockedApi.post).toHaveBeenCalledWith('/auth/logout', {
        refreshToken: 'refresh-token',
      });
      expect(localStorage.getItem('token')).toBeNull();
      expect(localStorage.getItem('refreshToken')).toBeNull();
    });

    it('clears tokens even if logout request fails', async () => {
      localStorage.setItem('token', 'access-token');
      localStorage.setItem('refreshToken', 'refresh-token');

      mockedApi.post.mockRejectedValueOnce(new Error('Logout failed'));

      await authService.logout();

      expect(localStorage.getItem('token')).toBeNull();
      expect(localStorage.getItem('refreshToken')).toBeNull();
    });
  });
});