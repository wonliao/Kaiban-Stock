import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Chip,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Divider,
  IconButton,
} from '@mui/material';
import {
  Close,
  TrendingUp,
  TrendingDown,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { Card as CardType } from '../../store/slices/kanbanSlice';
import StockChart from '../chart/StockChart';

interface CardDetailModalProps {
  open: boolean;
  card: CardType | null;
  onClose: () => void;
  onUpdateCard: (cardId: string, updates: Partial<CardType>) => void;
}

const CardDetailModal: React.FC<CardDetailModalProps> = ({
  open,
  card,
  onClose,
  onUpdateCard,
}) => {
  const { t } = useTranslation();
  const [note, setNote] = useState('');
  const [status, setStatus] = useState<CardType['status']>('watch');

  React.useEffect(() => {
    if (card) {
      setNote(card.note || '');
      setStatus(card.status);
    }
  }, [card]);

  if (!card) return null;

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
    return new Intl.NumberFormat('zh-TW').format(volume);
  };

  const getChangeColor = (changePercent: number | undefined) => {
    if (changePercent === undefined) return 'text.secondary';
    return changePercent > 0 ? 'success.main' : changePercent < 0 ? 'error.main' : 'text.secondary';
  };

  const getChangeIcon = (changePercent: number | undefined) => {
    if (changePercent === undefined) return null;
    return changePercent > 0 ? <TrendingUp /> : 
           changePercent < 0 ? <TrendingDown /> : null;
  };

  const handleSave = () => {
    onUpdateCard(card.id, {
      note: note.trim() || undefined,
      status,
    });
    onClose();
  };

  const statusOptions = [
    { value: 'watch', label: t('kanban.columns.watch', '觀察') },
    { value: 'readyToBuy', label: t('kanban.columns.readyToBuy', '準備買進') },
    { value: 'hold', label: t('kanban.columns.hold', '持有') },
    { value: 'sell', label: t('kanban.columns.sell', '賣出') },
    { value: 'alerts', label: t('kanban.columns.alerts', '警示') },
  ];

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      PaperProps={{
        sx: { minHeight: '60vh' }
      }}
    >
      <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="h6" component="span">
            {card.stockCode}
          </Typography>
          <Typography variant="subtitle1" color="text.secondary">
            {card.stockName}
          </Typography>
        </Box>
        <IconButton onClick={onClose} size="small">
          <Close />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
          {/* Price Information */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Typography variant="h4" fontWeight="bold">
              {formatPrice(card.currentPrice)}
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              {getChangeIcon(card.changePercent)}
              <Typography
                variant="h6"
                fontWeight="bold"
                color={getChangeColor(card.changePercent)}
              >
                {formatPercent(card.changePercent)}
              </Typography>
            </Box>
          </Box>

          {/* Technical Indicators */}
          <Box>
            <Typography variant="h6" gutterBottom>
              {t('chart.indicators.title', '技術指標')}
            </Typography>
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              {card.ma20 && (
                <Chip
                  label={`${t('chart.indicators.ma', 'MA')}20: ${formatPrice(card.ma20)}`}
                  variant="outlined"
                  color="primary"
                />
              )}
              {card.rsi && (
                <Chip
                  label={`${t('chart.indicators.rsi', 'RSI')}: ${card.rsi.toFixed(1)}`}
                  variant="outlined"
                  color={card.rsi > 70 ? 'error' : card.rsi < 30 ? 'success' : 'default'}
                />
              )}
            </Box>
          </Box>

          {/* Volume and Time */}
          <Box sx={{ display: 'flex', gap: 4 }}>
            <Box sx={{ flex: 1 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                {t('watchlist.volume', '成交量')}
              </Typography>
              <Typography variant="body1">
                {formatVolume(card.volume)}
              </Typography>
            </Box>
            <Box sx={{ flex: 1 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                {t('common.lastUpdated', '最後更新')}
              </Typography>
              <Typography variant="body1">
                {new Date(card.updatedAt).toLocaleString('zh-TW')}
              </Typography>
            </Box>
          </Box>

          <Divider />

          {/* Stock Chart */}
          <Box>
            <StockChart
              stockCode={card.stockCode}
              stockName={card.stockName}
              defaultPeriod="30d"
              height={300}
            />
          </Box>

          <Divider />

          {/* Status Selection and Note */}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Box sx={{ maxWidth: 300 }}>
              <FormControl fullWidth>
                <InputLabel>{t('kanban.status', '狀態')}</InputLabel>
                <Select
                  value={status}
                  label={t('kanban.status', '狀態')}
                  onChange={(e) => setStatus(e.target.value as CardType['status'])}
                >
                  {statusOptions.map((option) => (
                    <MenuItem key={option.value} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Box>

            <TextField
              fullWidth
              multiline
              rows={3}
              label={t('kanban.note', '備註')}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder={t('kanban.notePlaceholder', '新增您的投資筆記...')}
            />
          </Box>
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose}>
          {t('common.cancel', '取消')}
        </Button>
        <Button onClick={handleSave} variant="contained">
          {t('common.save', '儲存')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default CardDetailModal;