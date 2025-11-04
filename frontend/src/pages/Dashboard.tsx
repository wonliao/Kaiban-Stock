import React, { useEffect, useState } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  CardHeader,
  CircularProgress,
  Alert,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { kanbanService, KanbanStats } from '../services/kanbanService';

const Dashboard: React.FC = () => {
  const { t } = useTranslation();
  const [stats, setStats] = useState<KanbanStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await kanbanService.getStats();
        setStats(data);
      } catch (err: any) {
        setError(err.message || '獲取數據失敗');
        console.error('Failed to fetch stats:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchStats();
  }, []);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Box>
        <Typography variant="h4" component="h1" gutterBottom>
          {t('dashboard.title', '儀表板')}
        </Typography>
        <Alert severity="error">{error}</Alert>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>
        {t('dashboard.title', '儀表板')}
      </Typography>

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3 }}>
        <Box sx={{ flex: '1 1 300px', minWidth: 250 }}>
          <Card>
            <CardHeader title={t('dashboard.totalStocks', '總股票數')} />
            <CardContent>
              <Typography variant="h3" color="primary">
                {stats?.totalCards || 0}
              </Typography>
            </CardContent>
          </Card>
        </Box>

        <Box sx={{ flex: '1 1 300px', minWidth: 250 }}>
          <Card>
            <CardHeader title={t('dashboard.watchingStocks', '觀察中')} />
            <CardContent>
              <Typography variant="h3" color="info.main">
                {stats?.watchCount || 0}
              </Typography>
            </CardContent>
          </Card>
        </Box>

        <Box sx={{ flex: '1 1 300px', minWidth: 250 }}>
          <Card>
            <CardHeader title={t('dashboard.holdingStocks', '持有中')} />
            <CardContent>
              <Typography variant="h3" color="success.main">
                {stats?.holdCount || 0}
              </Typography>
            </CardContent>
          </Card>
        </Box>

        <Box sx={{ flex: '1 1 300px', minWidth: 250 }}>
          <Card>
            <CardHeader title={t('dashboard.alertStocks', '警示中')} />
            <CardContent>
              <Typography variant="h3" color="warning.main">
                {stats?.alertsCount || 0}
              </Typography>
            </CardContent>
          </Card>
        </Box>
      </Box>
    </Box>
  );
};

export default Dashboard;