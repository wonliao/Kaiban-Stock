import React, { useEffect, useState } from 'react';
import {
  Box,
  ButtonGroup,
  Button,
  CircularProgress,
  Typography,
  useTheme,
  Alert,
} from '@mui/material';
import {
  ComposedChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Area,
  AreaChart,
} from 'recharts';
import { useTranslation } from 'react-i18next';
import { ChartPeriod, OhlcData } from '../../types/chart';
import chartService from '../../services/chartService';

interface StockChartProps {
  stockCode: string;
  stockName?: string;
  defaultPeriod?: ChartPeriod;
  height?: number;
}

/**
 * 完整股票圖表元件
 * 顯示股價走勢、成交量和技術指標
 */
const StockChart: React.FC<StockChartProps> = ({
  stockCode,
  stockName,
  defaultPeriod = '30d',
  height = 400,
}) => {
  const { t } = useTranslation();
  const theme = useTheme();
  const [data, setData] = useState<OhlcData[]>([]);
  const [period, setPeriod] = useState<ChartPeriod>(defaultPeriod);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError(null);

      try {
        const chartData = await chartService.getChartData(stockCode, period);
        setData(chartData.data);
      } catch (err: any) {
        console.error(`Failed to load chart data for ${stockCode}:`, err);
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [stockCode, period]);

  const handlePeriodChange = (newPeriod: ChartPeriod) => {
    setPeriod(newPeriod);
  };

  // 格式化日期顯示
  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString('zh-TW', {
      month: 'short',
      day: 'numeric',
    });
  };

  // 格式化價格
  const formatPrice = (value: number) => {
    return `$${value.toFixed(2)}`;
  };

  // 格式化成交量
  const formatVolume = (value: number) => {
    if (value >= 1000000) {
      return `${(value / 1000000).toFixed(1)}M`;
    } else if (value >= 1000) {
      return `${(value / 1000).toFixed(1)}K`;
    }
    return value.toString();
  };

  // 自定義 Tooltip
  const CustomTooltip = ({ active, payload }: any) => {
    if (active && payload && payload.length > 0) {
      const data = payload[0].payload;
      const isUp = data.close >= data.open;

      return (
        <Box
          sx={{
            bgcolor: theme.palette.background.paper,
            p: 1.5,
            border: `1px solid ${theme.palette.divider}`,
            borderRadius: 1,
            boxShadow: 2,
          }}
        >
          <Typography variant="caption" sx={{ display: 'block', mb: 0.5 }}>
            {new Date(data.date).toLocaleDateString('zh-TW')}
          </Typography>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.25 }}>
            <Typography variant="caption">
              開: <strong>{formatPrice(data.open)}</strong>
            </Typography>
            <Typography variant="caption">
              高: <strong>{formatPrice(data.high)}</strong>
            </Typography>
            <Typography variant="caption">
              低: <strong>{formatPrice(data.low)}</strong>
            </Typography>
            <Typography
              variant="caption"
              sx={{
                color: isUp
                  ? theme.palette.success.main
                  : theme.palette.error.main,
              }}
            >
              收: <strong>{formatPrice(data.close)}</strong>
            </Typography>
            <Typography variant="caption" color="text.secondary">
              量: {formatVolume(data.volume)}
            </Typography>
          </Box>
        </Box>
      );
    }
    return null;
  };

  const periodButtons: { value: ChartPeriod; label: string }[] = [
    { value: '7d', label: '7天' },
    { value: '30d', label: '30天' },
    { value: '90d', label: '90天' },
    { value: '180d', label: '半年' },
    { value: '1y', label: '1年' },
  ];

  if (loading) {
    return (
      <Box
        sx={{
          height,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 2,
        }}
      >
        <CircularProgress />
        <Typography variant="body2" color="text.secondary">
          載入圖表資料中...
        </Typography>
      </Box>
    );
  }

  if (error) {
    return (
      <Box sx={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Alert severity="error" sx={{ maxWidth: 400 }}>
          {error}
        </Alert>
      </Box>
    );
  }

  if (data.length === 0) {
    return (
      <Box sx={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          無歷史資料
        </Typography>
      </Box>
    );
  }

  return (
    <Box>
      {/* Header with Period Selector */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          mb: 2,
        }}
      >
        <Box>
          {stockName && (
            <Typography variant="h6" component="span" sx={{ mr: 1 }}>
              {stockCode}
            </Typography>
          )}
          <Typography variant="body2" color="text.secondary" component="span">
            {stockName || stockCode}
          </Typography>
        </Box>

        <ButtonGroup size="small" variant="outlined">
          {periodButtons.map((btn) => (
            <Button
              key={btn.value}
              onClick={() => handlePeriodChange(btn.value)}
              variant={period === btn.value ? 'contained' : 'outlined'}
            >
              {btn.label}
            </Button>
          ))}
        </ButtonGroup>
      </Box>

      {/* Price Chart */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle2" gutterBottom>
          {t('chart.price', '股價走勢')}
        </Typography>
        <ResponsiveContainer width="100%" height={height}>
          <AreaChart data={data} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="colorPrice" x1="0" y1="0" x2="0" y2="1">
                <stop
                  offset="5%"
                  stopColor={theme.palette.primary.main}
                  stopOpacity={0.3}
                />
                <stop
                  offset="95%"
                  stopColor={theme.palette.primary.main}
                  stopOpacity={0}
                />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
            <XAxis
              dataKey="date"
              tickFormatter={formatDate}
              stroke={theme.palette.text.secondary}
              style={{ fontSize: '12px' }}
            />
            <YAxis
              domain={['dataMin - 5', 'dataMax + 5']}
              tickFormatter={formatPrice}
              stroke={theme.palette.text.secondary}
              style={{ fontSize: '12px' }}
            />
            <Tooltip content={<CustomTooltip />} />
            <Area
              type="monotone"
              dataKey="close"
              stroke={theme.palette.primary.main}
              strokeWidth={2}
              fillOpacity={1}
              fill="url(#colorPrice)"
            />
          </AreaChart>
        </ResponsiveContainer>
      </Box>

      {/* Volume Chart */}
      <Box>
        <Typography variant="subtitle2" gutterBottom>
          {t('chart.volume', '成交量')}
        </Typography>
        <ResponsiveContainer width="100%" height={120}>
          <ComposedChart data={data} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
            <XAxis
              dataKey="date"
              tickFormatter={formatDate}
              stroke={theme.palette.text.secondary}
              style={{ fontSize: '12px' }}
            />
            <YAxis
              tickFormatter={formatVolume}
              stroke={theme.palette.text.secondary}
              style={{ fontSize: '12px' }}
            />
            <Tooltip
              formatter={(value: number) => [formatVolume(value), '成交量']}
              labelFormatter={(label) => new Date(label).toLocaleDateString('zh-TW')}
            />
            <Bar
              dataKey="volume"
              fill={theme.palette.primary.light}
              opacity={0.6}
              radius={[4, 4, 0, 0]}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </Box>
    </Box>
  );
};

export default StockChart;
