import api from '../utils/api';

export interface KanbanStats {
  totalCards: number;
  watchCount: number;
  readyToBuyCount: number;
  holdCount: number;
  sellCount: number;
  alertsCount: number;
}

export interface KanbanStatsResponse {
  success: boolean;
  data: KanbanStats;
  meta: {
    timestamp: string;
    traceId: string;
    version: string;
  };
}

class KanbanService {
  async getStats(): Promise<KanbanStats> {
    try {
      const response = await api.get<KanbanStatsResponse>('/kanban/stats');
      return response.data.data;
    } catch (error: any) {
      console.error('Failed to fetch kanban stats:', error);
      const errorMessage = error.response?.data?.error?.message || '獲取統計數據失敗';
      throw new Error(errorMessage);
    }
  }
}

export const kanbanService = new KanbanService();
export default kanbanService;
