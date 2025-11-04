import React from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  CardHeader,
} from '@mui/material';
import { useTranslation } from 'react-i18next';

const Dashboard: React.FC = () => {
  const { t } = useTranslation();

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
                0
              </Typography>
            </CardContent>
          </Card>
        </Box>
        
        <Box sx={{ flex: '1 1 300px', minWidth: 250 }}>
          <Card>
            <CardHeader title={t('dashboard.watchingStocks', '觀察中')} />
            <CardContent>
              <Typography variant="h3" color="info.main">
                0
              </Typography>
            </CardContent>
          </Card>
        </Box>
        
        <Box sx={{ flex: '1 1 300px', minWidth: 250 }}>
          <Card>
            <CardHeader title={t('dashboard.holdingStocks', '持有中')} />
            <CardContent>
              <Typography variant="h3" color="success.main">
                0
              </Typography>
            </CardContent>
          </Card>
        </Box>
        
        <Box sx={{ flex: '1 1 300px', minWidth: 250 }}>
          <Card>
            <CardHeader title={t('dashboard.alertStocks', '警示中')} />
            <CardContent>
              <Typography variant="h3" color="warning.main">
                0
              </Typography>
            </CardContent>
          </Card>
        </Box>
      </Box>
    </Box>
  );
};

export default Dashboard;