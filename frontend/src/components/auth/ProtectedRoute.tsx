import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAppSelector } from '../../hooks/useAppSelector';
import { Box, CircularProgress, Typography, Button } from '@mui/material';
import { useTranslation } from 'react-i18next';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: 'ADMIN' | 'EDITOR' | 'VIEWER';
  requiredPermissions?: string[];
  allowedRoles?: ('ADMIN' | 'EDITOR' | 'VIEWER')[];
}

// Role hierarchy for permission checking
const ROLE_HIERARCHY = {
  ADMIN: 3,
  EDITOR: 2,
  VIEWER: 1,
};

// Permission mapping based on roles
const ROLE_PERMISSIONS = {
  ADMIN: ['read', 'write', 'delete', 'manage_users', 'manage_watchlist', 'admin_panel'],
  EDITOR: ['read', 'write', 'move_cards', 'edit_notes'],
  VIEWER: ['read'],
};

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  requiredRole,
  requiredPermissions = [],
  allowedRoles = [],
}) => {
  const { t } = useTranslation();
  const location = useLocation();
  const { isAuthenticated, user, loading, tokenRefreshing } = useAppSelector((state) => state.auth);

  // Show loading spinner while checking authentication or refreshing token
  if (loading || tokenRefreshing) {
    return (
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh',
          gap: 2,
        }}
      >
        <CircularProgress />
        <Typography variant="body2" color="text.secondary">
          {t('auth.verifying', '驗證中...')}
        </Typography>
      </Box>
    );
  }

  // Redirect to login if not authenticated
  if (!isAuthenticated || !user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Helper function to check if user has required role level
  const hasRequiredRoleLevel = (userRole: string, requiredRole: string): boolean => {
    const userLevel = ROLE_HIERARCHY[userRole as keyof typeof ROLE_HIERARCHY] || 0;
    const requiredLevel = ROLE_HIERARCHY[requiredRole as keyof typeof ROLE_HIERARCHY] || 0;
    return userLevel >= requiredLevel;
  };

  // Helper function to check if user has specific permissions
  const hasPermissions = (userRole: string, permissions: string[]): boolean => {
    const userPermissions = ROLE_PERMISSIONS[userRole as keyof typeof ROLE_PERMISSIONS] || [];
    return permissions.every(permission => userPermissions.includes(permission));
  };

  // Check role-based access
  if (requiredRole && !hasRequiredRoleLevel(user.role, requiredRole)) {
    return (
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh',
          gap: 2,
          p: 3,
        }}
      >
        <Typography variant="h5" color="error">
          {t('auth.accessDenied', '存取被拒絕')}
        </Typography>
        <Typography variant="body1" color="text.secondary" align="center">
          {t('auth.insufficientRole', '您沒有足夠的權限存取此頁面。')}
          <br />
          {t('auth.requiredRole', '需要角色')}: {t(`role.${requiredRole.toLowerCase()}`, requiredRole)}
          <br />
          {t('auth.yourRole', '您的角色')}: {t(`role.${user.role.toLowerCase()}`, user.role)}
        </Typography>
        <Button
          variant="outlined"
          onClick={() => window.history.back()}
          sx={{ mt: 2 }}
        >
          {t('common.goBack', '返回')}
        </Button>
      </Box>
    );
  }

  // Check allowed roles (alternative to requiredRole for multiple role support)
  if (allowedRoles.length > 0 && !allowedRoles.includes(user.role as any)) {
    return (
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh',
          gap: 2,
          p: 3,
        }}
      >
        <Typography variant="h5" color="error">
          {t('auth.accessDenied', '存取被拒絕')}
        </Typography>
        <Typography variant="body1" color="text.secondary" align="center">
          {t('auth.roleNotAllowed', '您的角色無權存取此頁面。')}
          <br />
          {t('auth.allowedRoles', '允許的角色')}: {allowedRoles.map(role => t(`role.${role.toLowerCase()}`, role)).join(', ')}
          <br />
          {t('auth.yourRole', '您的角色')}: {t(`role.${user.role.toLowerCase()}`, user.role)}
        </Typography>
        <Button
          variant="outlined"
          onClick={() => window.history.back()}
          sx={{ mt: 2 }}
        >
          {t('common.goBack', '返回')}
        </Button>
      </Box>
    );
  }

  // Check permission-based access
  if (requiredPermissions.length > 0 && !hasPermissions(user.role, requiredPermissions)) {
    return (
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh',
          gap: 2,
          p: 3,
        }}
      >
        <Typography variant="h5" color="error">
          {t('auth.insufficientPermissions', '權限不足')}
        </Typography>
        <Typography variant="body1" color="text.secondary" align="center">
          {t('auth.missingPermissions', '您沒有執行此操作的權限。')}
          <br />
          {t('auth.requiredPermissions', '需要權限')}: {requiredPermissions.join(', ')}
        </Typography>
        <Button
          variant="outlined"
          onClick={() => window.history.back()}
          sx={{ mt: 2 }}
        >
          {t('common.goBack', '返回')}
        </Button>
      </Box>
    );
  }

  return <>{children}</>;
};

export default ProtectedRoute;