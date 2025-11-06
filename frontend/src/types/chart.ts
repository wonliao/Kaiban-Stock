/**
 * OHLC (Open, High, Low, Close) 資料點
 */
export interface OhlcData {
  date: string; // ISO 8601 date string
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

/**
 * 圖表資料
 */
export interface ChartData {
  stockCode: string;
  data: OhlcData[];
  period: string; // "7d", "30d", "90d", "1y", "custom"
  startDate?: string;
  endDate?: string;
}

/**
 * 圖表時間範圍選項
 */
export type ChartPeriod = '7d' | '30d' | '90d' | '180d' | '1y';

/**
 * 圖表主題配色
 */
export interface ChartTheme {
  upColor: string;      // 上漲顏色
  downColor: string;    // 下跌顏色
  backgroundColor: string;
  textColor: string;
  gridColor: string;
  tooltipBg: string;
  tooltipText: string;
}
