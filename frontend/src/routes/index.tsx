import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAppSelector } from '../hooks/useAppSelector';
import Layout from '../components/layout/Layout';
import ProtectedRoute from '../components/auth/ProtectedRoute';
import TokenRefreshHandler from '../components/auth/TokenRefreshHandler';
import Dashboard from '../pages/Dashboard';
import Kanban from '../pages/Kanban';
import Watchlist from '../pages/Watchlist';
import Login from '../pages/Login';
import Register from '../pages/Register';

interface AppRoutesProps {
  onThemeToggle: () => void;
  isDarkMode: boolean;
}

const AppRoutes: React.FC<AppRoutesProps> = ({ onThemeToggle, isDarkMode }) => {
  const { isAuthenticated } = useAppSelector((state) => state.auth);

  return (
    <>
      {/* Token refresh handler runs in background */}
      <TokenRefreshHandler />
      
      <Routes>
        {/* Public routes */}
        <Route 
          path="/login" 
          element={
            isAuthenticated ? <Navigate to="/dashboard" replace /> : <Login />
          } 
        />
        <Route 
          path="/register" 
          element={
            isAuthenticated ? <Navigate to="/dashboard" replace /> : <Register />
          } 
        />
        
        {/* Protected routes */}
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <Layout onThemeToggle={onThemeToggle} isDarkMode={isDarkMode}>
                <Dashboard />
              </Layout>
            </ProtectedRoute>
          }
        />
        
        <Route
          path="/kanban"
          element={
            <ProtectedRoute>
              <Layout onThemeToggle={onThemeToggle} isDarkMode={isDarkMode}>
                <Kanban />
              </Layout>
            </ProtectedRoute>
          }
        />
        
        <Route
          path="/watchlist"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'EDITOR']} requiredPermissions={['manage_watchlist']}>
              <Layout onThemeToggle={onThemeToggle} isDarkMode={isDarkMode}>
                <Watchlist />
              </Layout>
            </ProtectedRoute>
          }
        />
        
        {/* Admin-only routes */}
        <Route
          path="/admin"
          element={
            <ProtectedRoute requiredRole="ADMIN" requiredPermissions={['admin_panel']}>
              <Layout onThemeToggle={onThemeToggle} isDarkMode={isDarkMode}>
                <div>Admin Panel (Coming Soon)</div>
              </Layout>
            </ProtectedRoute>
          }
        />
        
        {/* User management - Admin only */}
        <Route
          path="/admin/users"
          element={
            <ProtectedRoute requiredRole="ADMIN" requiredPermissions={['manage_users']}>
              <Layout onThemeToggle={onThemeToggle} isDarkMode={isDarkMode}>
                <div>User Management (Coming Soon)</div>
              </Layout>
            </ProtectedRoute>
          }
        />
        
        {/* Other protected routes */}
        <Route
          path="/notifications"
          element={
            <ProtectedRoute>
              <Layout onThemeToggle={onThemeToggle} isDarkMode={isDarkMode}>
                <div>Notifications (Coming Soon)</div>
              </Layout>
            </ProtectedRoute>
          }
        />
        
        <Route
          path="/settings"
          element={
            <ProtectedRoute>
              <Layout onThemeToggle={onThemeToggle} isDarkMode={isDarkMode}>
                <div>Settings (Coming Soon)</div>
              </Layout>
            </ProtectedRoute>
          }
        />
        
        <Route
          path="/help"
          element={
            <ProtectedRoute>
              <Layout onThemeToggle={onThemeToggle} isDarkMode={isDarkMode}>
                <div>Help (Coming Soon)</div>
              </Layout>
            </ProtectedRoute>
          }
        />
        
        {/* Default redirects */}
        <Route 
          path="/" 
          element={
            isAuthenticated ? 
              <Navigate to="/dashboard" replace /> : 
              <Navigate to="/login" replace />
          } 
        />
        
        <Route 
          path="*" 
          element={
            isAuthenticated ? 
              <Navigate to="/dashboard" replace /> : 
              <Navigate to="/login" replace />
          } 
        />
      </Routes>
    </>
  );
};

export default AppRoutes;