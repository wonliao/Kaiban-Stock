import React, { useState, useEffect } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { ThemeProvider } from '@mui/material/styles';
import { CssBaseline } from '@mui/material';
import { store } from './store';
import { getTheme } from './theme';
import AppRoutes from './routes';
import './i18n';

function App() {
  const [themeMode, setThemeMode] = useState<'light' | 'dark' | 'auto'>(() => {
    return (localStorage.getItem('theme') as 'light' | 'dark' | 'auto') || 'auto';
  });

  const [isDarkMode, setIsDarkMode] = useState(false);

  useEffect(() => {
    const theme = getTheme(themeMode);
    setIsDarkMode(theme.palette.mode === 'dark');
  }, [themeMode]);

  const handleThemeToggle = () => {
    const newMode = isDarkMode ? 'light' : 'dark';
    setThemeMode(newMode);
    localStorage.setItem('theme', newMode);
  };

  const theme = getTheme(themeMode);

  return (
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <BrowserRouter>
          <AppRoutes onThemeToggle={handleThemeToggle} isDarkMode={isDarkMode} />
        </BrowserRouter>
      </ThemeProvider>
    </Provider>
  );
}

export default App;
