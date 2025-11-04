import React from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  Chip,
  IconButton,
  Tooltip,
} from '@mui/material';
import {
  TrendingUp,
  TrendingDown,
  Info,
  DragIndicator,
} from '@mui/icons-material';
import { Draggable } from 'react-beautiful-dnd';
import { useTranslation } from 'react-i18next';
import { Card as CardType } from '../../store/slices/kanbanSlice';

interface KanbanCardProps {
  card: CardType;
  index: number;
  onCardClick: (card: CardType) => void;
}

const KanbanCard: React.FC<KanbanCardProps> = ({ card, index, onCardClick }) => {
  const { t } = useTranslation();

  const formatPrice = (price: number | undefined) => {
    if (price === undefined) return '--';
    return new Intl.NumberFormat('zh-TW', {
      style: 'currency',
      currency: 'TWD',
      minimumFractionDigits: 2,
    }).format(price);
  };

  const formatPercent = (percent: number | undefined) => {
    if (percent === undefined) return '--';
    return `${percent > 0 ? '+' : ''}${percent.toFixed(2)}%`;
  };

  const formatVolume = (volume: number | undefined) => {
    if (volume === undefined) return '--';
    if (volume >= 1000000) {
      return `${(volume / 1000000).toFixed(1)}M`;
    } else if (volume >= 1000) {
      return `${(volume / 1000).toFixed(1)}K`;
    }
    return volume.toString();
  };

  const getChangeColor = (changePercent: number | undefined) => {
    if (changePercent === undefined) return 'text.secondary';
    return changePercent > 0 ? 'success.main' : changePercent < 0 ? 'error.main' : 'text.secondary';
  };

  const getChangeIcon = (changePercent: number | undefined) => {
    if (changePercent === undefined) return null;
    return changePercent > 0 ? <TrendingUp fontSize="small" /> : 
           changePercent < 0 ? <TrendingDown fontSize="small" /> : null;
  };

  return (
    <Draggable draggableId={card.id} index={index}>
      {(provided, snapshot) => (
        <Card
          ref={provided.innerRef}
          {...provided.draggableProps}
          sx={{
            mb: 1,
            cursor: 'pointer',
            transform: snapshot.isDragging ? 'rotate(5deg)' : 'none',
            boxShadow: snapshot.isDragging ? 4 : 1,
            '&:hover': {
              boxShadow: 2,
            },
          }}
          onClick={() => onCardClick(card)}
        >
          <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
            {/* Drag Handle */}
            <Box
              {...provided.dragHandleProps}
              sx={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'flex-start',
                mb: 1,
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <DragIndicator sx={{ color: 'text.disabled', fontSize: 16 }} />
                <Typography variant="subtitle2" fontWeight="bold">
                  {card.stockCode}
                </Typography>
              </Box>
              <Tooltip title={t('common.info', '詳細資訊')}>
                <IconButton size="small" sx={{ p: 0.5 }}>
                  <Info fontSize="small" />
                </IconButton>
              </Tooltip>
            </Box>

            {/* Stock Name */}
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              {card.stockName}
            </Typography>

            {/* Price Information */}
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="h6" fontWeight="bold">
                {formatPrice(card.currentPrice)}
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                {getChangeIcon(card.changePercent)}
                <Typography
                  variant="body2"
                  fontWeight="bold"
                  color={getChangeColor(card.changePercent)}
                >
                  {formatPercent(card.changePercent)}
                </Typography>
              </Box>
            </Box>

            {/* Technical Indicators */}
            <Box sx={{ display: 'flex', gap: 1, mb: 1, flexWrap: 'wrap' }}>
              {card.ma20 && (
                <Chip
                  label={`MA20: ${formatPrice(card.ma20)}`}
                  size="small"
                  variant="outlined"
                  sx={{ fontSize: '0.7rem', height: 20 }}
                />
              )}
              {card.rsi && (
                <Chip
                  label={`RSI: ${card.rsi.toFixed(1)}`}
                  size="small"
                  variant="outlined"
                  color={card.rsi > 70 ? 'error' : card.rsi < 30 ? 'success' : 'default'}
                  sx={{ fontSize: '0.7rem', height: 20 }}
                />
              )}
            </Box>

            {/* Volume */}
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="caption" color="text.secondary">
                {t('watchlist.volume', '成交量')}: {formatVolume(card.volume)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {new Date(card.updatedAt).toLocaleTimeString('zh-TW', {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </Typography>
            </Box>

            {/* Note */}
            {card.note && (
              <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                {card.note}
              </Typography>
            )}
          </CardContent>
        </Card>
      )}
    </Draggable>
  );
};

export default KanbanCard;