import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Alert,
  CircularProgress,
  Snackbar,
} from '@mui/material';
import { DragDropContext, DropResult } from 'react-beautiful-dnd';
import { useTranslation } from 'react-i18next';
import { useAppDispatch, useAppSelector } from '../../hooks/useAppDispatch';
import { 
  Card as CardType, 
  updateCardStatus,
  fetchCardsStart,
  fetchCardsSuccess,
} from '../../store/slices/kanbanSlice';
import KanbanColumn from './KanbanColumn';
import CardDetailModal from './CardDetailModal';

const KanbanBoard: React.FC = () => {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const { cards, loading, error, searchQuery, statusFilter } = useAppSelector(state => state.kanban);
  
  const [selectedCard, setSelectedCard] = useState<CardType | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  // Mock data for development - this will be replaced with API calls in later tasks
  const mockCards: CardType[] = [
    {
      id: '1',
      stockCode: '2330',
      stockName: '台積電',
      status: 'watch',
      currentPrice: 580.00,
      changePercent: 2.5,
      volume: 25000000,
      ma20: 575.00,
      rsi: 65.5,
      note: '等待突破前高',
      createdAt: '2024-01-15T09:00:00Z',
      updatedAt: '2024-01-15T14:30:00Z',
    },
    {
      id: '2',
      stockCode: '2317',
      stockName: '鴻海',
      status: 'readyToBuy',
      currentPrice: 105.50,
      changePercent: -1.2,
      volume: 18500000,
      ma20: 107.20,
      rsi: 45.2,
      createdAt: '2024-01-15T09:00:00Z',
      updatedAt: '2024-01-15T14:25:00Z',
    },
    {
      id: '3',
      stockCode: '2454',
      stockName: '聯發科',
      status: 'hold',
      currentPrice: 920.00,
      changePercent: 3.8,
      volume: 8200000,
      ma20: 885.50,
      rsi: 72.1,
      note: '持續觀察RSI是否過熱',
      createdAt: '2024-01-15T09:00:00Z',
      updatedAt: '2024-01-15T14:20:00Z',
    },
    {
      id: '4',
      stockCode: '2412',
      stockName: '中華電',
      status: 'sell',
      currentPrice: 125.50,
      changePercent: 0.8,
      volume: 5600000,
      ma20: 124.80,
      rsi: 58.3,
      createdAt: '2024-01-15T09:00:00Z',
      updatedAt: '2024-01-15T14:15:00Z',
    },
    {
      id: '5',
      stockCode: '2881',
      stockName: '富邦金',
      status: 'alerts',
      currentPrice: 68.20,
      changePercent: -3.2,
      volume: 12800000,
      ma20: 70.50,
      rsi: 28.7,
      note: '跌破重要支撐',
      createdAt: '2024-01-15T09:00:00Z',
      updatedAt: '2024-01-15T14:10:00Z',
    },
  ];

  // Load mock data on component mount
  useEffect(() => {
    dispatch(fetchCardsStart());
    // Simulate API call delay
    setTimeout(() => {
      dispatch(fetchCardsSuccess(mockCards));
    }, 1000);
  }, [dispatch]); // mockCards is static, so it's safe to omit from dependencies

  const columns = [
    { 
      id: 'watch', 
      title: t('kanban.columns.watch', '觀察'), 
      color: '#e3f2fd' 
    },
    { 
      id: 'readyToBuy', 
      title: t('kanban.columns.readyToBuy', '準備買進'), 
      color: '#f3e5f5' 
    },
    { 
      id: 'hold', 
      title: t('kanban.columns.hold', '持有'), 
      color: '#e8f5e8' 
    },
    { 
      id: 'sell', 
      title: t('kanban.columns.sell', '賣出'), 
      color: '#fff3e0' 
    },
    { 
      id: 'alerts', 
      title: t('kanban.columns.alerts', '警示'), 
      color: '#ffebee' 
    },
  ];

  // Filter cards based on search query and status filter
  const filteredCards = cards.filter(card => {
    const matchesSearch = !searchQuery || 
      card.stockCode.toLowerCase().includes(searchQuery.toLowerCase()) ||
      card.stockName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (card.note && card.note.toLowerCase().includes(searchQuery.toLowerCase()));
    
    const matchesStatus = !statusFilter || card.status === statusFilter;
    
    return matchesSearch && matchesStatus;
  });

  // Group cards by status
  const cardsByStatus = columns.reduce((acc, column) => {
    acc[column.id] = filteredCards.filter(card => card.status === column.id);
    return acc;
  }, {} as Record<string, CardType[]>);

  const handleDragEnd = (result: DropResult) => {
    const { destination, source, draggableId } = result;

    // If dropped outside a droppable area
    if (!destination) {
      return;
    }

    // If dropped in the same position
    if (
      destination.droppableId === source.droppableId &&
      destination.index === source.index
    ) {
      return;
    }

    // Update card status
    const newStatus = destination.droppableId as CardType['status'];
    dispatch(updateCardStatus({ cardId: draggableId, status: newStatus }));
    
    // Show success message
    const card = cards.find(c => c.id === draggableId);
    const targetColumn = columns.find(c => c.id === newStatus);
    if (card && targetColumn) {
      setSnackbarMessage(
        t('kanban.moveSuccess', '已將 {{stockCode}} 移至 {{column}}', {
          stockCode: card.stockCode,
          column: targetColumn.title,
        })
      );
      setSnackbarOpen(true);
    }
  };

  const handleCardClick = (card: CardType) => {
    setSelectedCard(card);
    setModalOpen(true);
  };

  const handleUpdateCard = (cardId: string, updates: Partial<CardType>) => {
    // In a real application, this would make an API call
    // For now, we'll just update the Redux store
    if (updates.status) {
      dispatch(updateCardStatus({ cardId, status: updates.status }));
    }
    
    // Show success message
    setSnackbarMessage(t('kanban.updateSuccess', '卡片已更新'));
    setSnackbarOpen(true);
  };

  const handleCloseModal = () => {
    setModalOpen(false);
    setSelectedCard(null);
  };

  const handleCloseSnackbar = () => {
    setSnackbarOpen(false);
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress />
        <Typography variant="body1" sx={{ ml: 2 }}>
          {t('common.loading', '載入中...')}
        </Typography>
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {error}
      </Alert>
    );
  }

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>
        {t('kanban.title', '股票看板')}
      </Typography>
      
      <DragDropContext onDragEnd={handleDragEnd}>
        <Box sx={{ display: 'flex', gap: 2, overflowX: 'auto', pb: 2 }}>
          {columns.map((column) => (
            <KanbanColumn
              key={column.id}
              columnId={column.id}
              title={column.title}
              cards={cardsByStatus[column.id] || []}
              color={column.color}
              onCardClick={handleCardClick}
            />
          ))}
        </Box>
      </DragDropContext>

      <CardDetailModal
        open={modalOpen}
        card={selectedCard}
        onClose={handleCloseModal}
        onUpdateCard={handleUpdateCard}
      />

      <Snackbar
        open={snackbarOpen}
        autoHideDuration={3000}
        onClose={handleCloseSnackbar}
        message={snackbarMessage}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </Box>
  );
};

export default KanbanBoard;