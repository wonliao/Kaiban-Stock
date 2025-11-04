import { useAppSelector } from './useAppSelector';

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

export const usePermissions = () => {
  const { user, isAuthenticated } = useAppSelector((state) => state.auth);

  const hasRole = (requiredRole: string): boolean => {
    if (!isAuthenticated || !user) return false;
    
    const userLevel = ROLE_HIERARCHY[user.role as keyof typeof ROLE_HIERARCHY] || 0;
    const requiredLevel = ROLE_HIERARCHY[requiredRole as keyof typeof ROLE_HIERARCHY] || 0;
    return userLevel >= requiredLevel;
  };

  const hasPermission = (permission: string): boolean => {
    if (!isAuthenticated || !user) return false;
    
    const userPermissions = ROLE_PERMISSIONS[user.role as keyof typeof ROLE_PERMISSIONS] || [];
    return userPermissions.includes(permission);
  };

  const hasAnyPermission = (permissions: string[]): boolean => {
    return permissions.some(permission => hasPermission(permission));
  };

  const hasAllPermissions = (permissions: string[]): boolean => {
    return permissions.every(permission => hasPermission(permission));
  };

  const canManageWatchlist = (): boolean => {
    return hasPermission('manage_watchlist') || hasRole('ADMIN');
  };

  const canEditCards = (): boolean => {
    return hasPermission('write') || hasPermission('move_cards');
  };

  const canViewAdminPanel = (): boolean => {
    return hasPermission('admin_panel');
  };

  const canManageUsers = (): boolean => {
    return hasPermission('manage_users');
  };

  const isAdmin = (): boolean => {
    return hasRole('ADMIN');
  };

  const isEditor = (): boolean => {
    return hasRole('EDITOR');
  };

  const isViewer = (): boolean => {
    return user?.role === 'VIEWER';
  };

  return {
    user,
    isAuthenticated,
    hasRole,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    canManageWatchlist,
    canEditCards,
    canViewAdminPanel,
    canManageUsers,
    isAdmin,
    isEditor,
    isViewer,
    userRole: user?.role,
    userPermissions: user ? ROLE_PERMISSIONS[user.role as keyof typeof ROLE_PERMISSIONS] || [] : [],
  };
};

export default usePermissions;