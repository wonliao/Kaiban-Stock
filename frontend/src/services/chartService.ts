import api from '../utils/api';
import { ChartData, ChartPeriod } from '../types/chart';

class ChartService {
  /**
   * 取得股票圖表資料
   * @param stockCode 股票代碼
   * @param period 時間範圍
   */
  async getChartData(stockCode: string, period: ChartPeriod = '30d'): Promise<ChartData> {
    try {
      // Convert period to days
      const daysMap: Record<ChartPeriod, number> = {
        '7d': 7,
        '30d': 30,
        '90d': 90,
        '180d': 180,
        '1y': 365,
      };

      const days = daysMap[period];
      const response = await api.get<ChartData>(`/chart/stocks/${stockCode}`, {
        params: { days },
      });

      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch chart data:', error);
      throw new Error(error.response?.data?.message || '取得圖表資料失敗');
    }
  }

  /**
   * 取得股票指定日期範圍的圖表資料
   * @param stockCode 股票代碼
   * @param startDate 開始日期 (YYYY-MM-DD)
   * @param endDate 結束日期 (YYYY-MM-DD)
   */
  async getChartDataByDateRange(
    stockCode: string,
    startDate: string,
    endDate: string
  ): Promise<ChartData> {
    try {
      const response = await api.get<ChartData>(`/chart/stocks/${stockCode}/range`, {
        params: {
          startDate,
          endDate,
        },
      });

      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch chart data by date range:', error);
      throw new Error(error.response?.data?.message || '取得圖表資料失敗');
    }
  }
}

const chartService = new ChartService();
export default chartService;
