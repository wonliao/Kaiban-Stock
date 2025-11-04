export const CARD_STATUS = {
  WATCH: 'watch',
  READY_TO_BUY: 'readyToBuy',
  HOLD: 'hold',
  SELL: 'sell',
  ALERTS: 'alerts',
} as const;

export const SORT_OPTIONS = {
  UPDATED_AT: 'updatedAt',
  CHANGE_PERCENT: 'changePercent',
  VOLUME: 'volume',
} as const;

export const SORT_ORDER = {
  ASC: 'asc',
  DESC: 'desc',
} as const;

export const SUPPORTED_LANGUAGES = [
  { code: 'zh-TW', name: '繁體中文' },
  { code: 'en-US', name: 'English' },
] as const;

export const THEME_MODES = {
  LIGHT: 'light',
  DARK: 'dark',
  AUTO: 'auto',
} as const;