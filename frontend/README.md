# Taiwan Stock Kanban Tracker - Frontend

This is the React frontend application for the Taiwan Stock Kanban Tracker system.

## Features

- **React 18** with TypeScript for type safety
- **Material-UI (MUI)** for consistent UI components
- **Redux Toolkit** for state management
- **React Router** for navigation
- **i18next** for internationalization (Chinese Traditional & English)
- **Axios** for API communication
- **Dark/Light theme** support

## Project Structure

```
src/
├── components/          # Reusable UI components
│   └── layout/         # Layout components (Header, Sidebar, Layout)
├── pages/              # Page components
├── store/              # Redux store and slices
├── routes/             # Routing configuration
├── hooks/              # Custom React hooks
├── utils/              # Utility functions and constants
├── theme/              # Material-UI theme configuration
└── i18n/               # Internationalization setup and translations
```

## Available Scripts

- `npm start` - Runs the app in development mode
- `npm test` - Launches the test runner
- `npm run build` - Builds the app for production
- `npm run eject` - Ejects from Create React App (one-way operation)

## Getting Started

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start the development server:
   ```bash
   npm start
   ```

3. Open [http://localhost:3000](http://localhost:3000) to view it in the browser.

## Environment Variables

Create a `.env` file in the root directory with:

```
REACT_APP_API_BASE_URL=http://localhost:8080/api
REACT_APP_WS_BASE_URL=ws://localhost:8080/ws
REACT_APP_VERSION=1.0.0
```

## State Management

The application uses Redux Toolkit with the following slices:

- **authSlice** - User authentication and authorization
- **kanbanSlice** - Kanban board cards and operations
- **stockSlice** - Stock data and snapshots
- **uiSlice** - UI state (theme, language, notifications)

## Internationalization

The app supports multiple languages:
- Traditional Chinese (zh-TW) - Default
- English (en-US)

Language files are located in `src/i18n/locales/`.

## Theme Support

The application supports:
- Light theme
- Dark theme  
- Auto theme (follows system preference)

Theme preference is persisted in localStorage.