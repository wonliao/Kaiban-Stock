import React from 'react';
import {
  AppBar,
  Toolbar,
  Typography,
  Box,
  IconButton,
  Menu,
  MenuItem,
  Avatar,
  Divider,
} from '@mui/material';
import {
  Language,
  Brightness4,
  Brightness7,
  Logout,
  Settings,
  Person,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch } from '../../hooks/useAppDispatch';
import { useAppSelector } from '../../hooks/useAppSelector';
import { logoutAsync } from '../../store/slices/authSlice';

interface HeaderProps {
  onThemeToggle: () => void;
  isDarkMode: boolean;
}

const Header: React.FC<HeaderProps> = ({ onThemeToggle, isDarkMode }) => {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const [langAnchorEl, setLangAnchorEl] = React.useState<null | HTMLElement>(null);

  const handleMenu = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleLangMenu = (event: React.MouseEvent<HTMLElement>) => {
    setLangAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
    setLangAnchorEl(null);
  };

  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng);
    localStorage.setItem('language', lng);
    handleClose();
  };

  const handleLogout = async () => {
    try {
      await dispatch(logoutAsync()).unwrap();
      navigate('/login');
    } catch (error) {
      console.error('Logout error:', error);
      // Force navigation even if logout fails
      navigate('/login');
    }
    handleClose();
  };

  const handleProfile = () => {
    navigate('/profile');
    handleClose();
  };

  const handleSettings = () => {
    navigate('/settings');
    handleClose();
  };

  return (
    <AppBar position="static">
      <Toolbar>
        <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
          {t('app.title', '台股看板追蹤')}
        </Typography>
        
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {/* Language Selector */}
          <IconButton
            size="large"
            onClick={handleLangMenu}
            color="inherit"
          >
            <Language />
          </IconButton>
          <Menu
            anchorEl={langAnchorEl}
            open={Boolean(langAnchorEl)}
            onClose={handleClose}
          >
            <MenuItem onClick={() => changeLanguage('zh-TW')}>繁體中文</MenuItem>
            <MenuItem onClick={() => changeLanguage('en-US')}>English</MenuItem>
          </Menu>

          {/* Theme Toggle */}
          <IconButton onClick={onThemeToggle} color="inherit">
            {isDarkMode ? <Brightness7 /> : <Brightness4 />}
          </IconButton>

          {/* User Menu */}
          <IconButton
            size="large"
            onClick={handleMenu}
            color="inherit"
            sx={{ p: 0.5 }}
          >
            <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main' }}>
              {user?.username?.charAt(0).toUpperCase() || 'U'}
            </Avatar>
          </IconButton>
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleClose}
            PaperProps={{
              sx: {
                mt: 1.5,
                minWidth: 200,
              },
            }}
          >
            <Box sx={{ px: 2, py: 1 }}>
              <Typography variant="subtitle2" noWrap>
                {user?.username || 'User'}
              </Typography>
              <Typography variant="body2" color="text.secondary" noWrap>
                {user?.email}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {t(`role.${user?.role?.toLowerCase()}`, { defaultValue: user?.role })}
              </Typography>
            </Box>
            <Divider />
            <MenuItem onClick={handleProfile}>
              <Person sx={{ mr: 1 }} />
              {t('header.profile', '個人資料')}
            </MenuItem>
            <MenuItem onClick={handleSettings}>
              <Settings sx={{ mr: 1 }} />
              {t('header.settings', '設定')}
            </MenuItem>
            <Divider />
            <MenuItem onClick={handleLogout} sx={{ color: 'error.main' }}>
              <Logout sx={{ mr: 1 }} />
              {t('header.logout', '登出')}
            </MenuItem>
          </Menu>
        </Box>
      </Toolbar>
    </AppBar>
  );
};

export default Header;