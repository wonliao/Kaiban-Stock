import React, { useEffect, useState } from 'react';
import { Box, CircularProgress, Typography, useTheme } from '@mui/material';
import { LineChart, Line, ResponsiveContainer, YAxis } from 'recharts';
import { OhlcData } from '../../types/chart';
import chartService from '../../services/chartService';

interface MiniChartProps {
  stockCode: string;
  height?: number;
  period?: '7d' | '30d';
  showError?: boolean;
}

/**
 * 迷你圖表元件
 * 用於卡片上顯示簡化的股價走勢
 */
const MiniChart: React.FC<MiniChartProps> = ({
  stockCode,
  height = 60,
  period = '7d',
  showError = false,
}) => {
  const theme = useTheme();
  const [data, setData] = useState<OhlcData[]>([]);
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
        console.error(`Failed to load mini chart for ${stockCode}:`, err);
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [stockCode, period]);

  // 計算漲跌趨勢
  const getTrendColor = () => {
    if (data.length < 2) return theme.palette.text.secondary;

    const firstClose = data[0].close;
    const lastClose = data[data.length - 1].close;

    if (lastClose > firstClose) {
      return theme.palette.success.main;
    } else if (lastClose < firstClose) {
      return theme.palette.error.main;
    }
    return theme.palette.text.secondary;
  };

  if (loading) {
    return (
      <Box
        sx={{
          height,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <CircularProgress size={20} />
      </Box>
    );
  }

  if (error) {
    if (!showError) return null;

    return (
      <Box
        sx={{
          height,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Typography variant="caption" color="text.disabled">
          無圖表資料
        </Typography>
      </Box>
    );
  }

  if (data.length === 0) {
    return (
      <Box
        sx={{
          height,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Typography variant="caption" color="text.disabled">
          無歷史資料
        </Typography>
      </Box>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 5, right: 0, left: 0, bottom: 5 }}>
        <YAxis hide domain={['dataMin', 'dataMax']} />
        <Line
          type="monotone"
          dataKey="close"
          stroke={getTrendColor()}
          strokeWidth={1.5}
          dot={false}
          animationDuration={300}
        />
      </LineChart>
    </ResponsiveContainer>
  );
};

export default MiniChart;
