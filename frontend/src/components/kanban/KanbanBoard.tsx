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
import { useAppDispatch } from '../../hooks/useAppDispatch';
import { useAppSelector } from '../../hooks/useAppSelector';
import {
  Card as CardType,
  updateCardStatus,
  fetchCardsStart,
  fetchCardsSuccess,
  fetchCardsFailure,
} from '../../store/slices/kanbanSlice';
import KanbanColumn from './KanbanColumn';
import CardDetailModal from './CardDetailModal';
import kanbanService from '../../services/kanbanService';

const KanbanBoard: React.FC = () => {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const { cards, loading, error, searchQuery, statusFilter } = useAppSelector(state => state.kanban);
  
  const [selectedCard, setSelectedCard] = useState<CardType | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  // Load cards from API on component mount
  useEffect(() => {
    const loadCards = async () => {
      dispatch(fetchCardsStart());
      try {
        const cards = await kanbanService.getCards();
        dispatch(fetchCardsSuccess(cards as any));
      } catch (error: any) {
        console.error('[KanbanBoard] Failed to load cards:', error);
        dispatch(fetchCardsFailure(error.message || '載入卡片失敗'));
      }
    };

    loadCards();
  }, [dispatch]);

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