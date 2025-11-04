import React from 'react';
import {
  Box,
  Typography,
  Paper,
  Badge,
} from '@mui/material';
import { Droppable } from 'react-beautiful-dnd';
import { useTranslation } from 'react-i18next';
import { Card as CardType } from '../../store/slices/kanbanSlice';
import KanbanCard from './KanbanCard';

interface KanbanColumnProps {
  columnId: string;
  title: string;
  cards: CardType[];
  color: string;
  onCardClick: (card: CardType) => void;
}

const KanbanColumn: React.FC<KanbanColumnProps> = ({
  columnId,
  title,
  cards,
  color,
  onCardClick,
}) => {
  const { t } = useTranslation();

  return (
    <Box sx={{ minWidth: 280, flex: '0 0 auto' }}>
      <Paper
        sx={{
          p: 2,
          minHeight: '70vh',
          backgroundColor: color,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {/* Column Header */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6" component="h2" fontWeight="bold">
            {title}
          </Typography>
          <Badge badgeContent={cards.length} color="primary" max={999}>
            <Box sx={{ width: 20, height: 20 }} />
          </Badge>
        </Box>

        {/* Droppable Area */}
        <Droppable droppableId={columnId}>
          {(provided, snapshot) => (
            <Box
              ref={provided.innerRef}
              {...provided.droppableProps}
              sx={{
                flex: 1,
                minHeight: 200,
                backgroundColor: snapshot.isDraggingOver ? 'rgba(0, 0, 0, 0.05)' : 'transparent',
                borderRadius: 1,
                transition: 'background-color 0.2s ease',
                p: snapshot.isDraggingOver ? 1 : 0,
              }}
            >
              {cards.length === 0 ? (
                <Box
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    height: 100,
                    border: '2px dashed',
                    borderColor: 'divider',
                    borderRadius: 1,
                    color: 'text.secondary',
                  }}
                >
                  <Typography variant="body2">
                    {t('kanban.noCards', '暫無卡片')}
                  </Typography>
                </Box>
              ) : (
                cards.map((card, index) => (
                  <KanbanCard
                    key={card.id}
                    card={card}
                    index={index}
                    onCardClick={onCardClick}
                  />
                ))
              )}
              {provided.placeholder}
            </Box>
          )}
        </Droppable>
      </Paper>
    </Box>
  );
};

export default KanbanColumn;