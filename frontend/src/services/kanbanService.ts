import api from '../utils/api';

export interface Card {
  id: string;
  stockCode: string;
  stockName: string;
  status: 'watch' | 'readyToBuy' | 'hold' | 'sell' | 'alerts';
  note?: string;
  currentPrice?: number;
  changePercent?: number;
  volume?: number;
  ma20?: number;
  rsi?: number;
  createdAt: string;
  updatedAt: string;
}

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

export interface CardsResponse {
  success: boolean;
  data: Card[];
  pagination: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
  };
  meta: {
    timestamp: string;
    traceId: string;
    version: string;
  };
}

class KanbanService {
  async getCards(params?: {
    q?: string;
    status?: string;
    page?: number;
    size?: number;
    sort?: string;
    direction?: string;
  }): Promise<Card[]> {
    try {
      const response = await api.get<CardsResponse>('/kanban/cards', { params });
      // Map backend status to frontend status format
      return response.data.data.map(card => ({
        ...card,
        status: this.mapBackendStatusToFrontend(card.status as any)
      }));
    } catch (error: any) {
      console.error('Failed to fetch kanban cards:', error);
      const errorMessage = error.response?.data?.error?.message || '獲取卡片失敗';
      throw new Error(errorMessage);
    }
  }

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

  private mapBackendStatusToFrontend(backendStatus: string): Card['status'] {
    const statusMap: Record<string, Card['status']> = {
      'WATCH': 'watch',
      'READY_TO_BUY': 'readyToBuy',
      'HOLD': 'hold',
      'SELL': 'sell',
      'ALERTS': 'alerts'
    };
    return statusMap[backendStatus] || 'watch';
  }
}

export const kanbanService = new KanbanService();
export default kanbanService;
