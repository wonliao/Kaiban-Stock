import React from 'react';
import {
  Box,
  Typography,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
} from '@mui/material';
import { Add } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

const Watchlist: React.FC = () => {
  const { t } = useTranslation();

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" component="h1">
          {t('watchlist.title', '觀察清單')}
        </Typography>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={() => {/* TODO: Implement add stock */}}
        >
          {t('watchlist.addStock', '新增股票')}
        </Button>
      </Box>
      
      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>{t('watchlist.stockCode', '股票代碼')}</TableCell>
              <TableCell>{t('watchlist.stockName', '股票名稱')}</TableCell>
              <TableCell align="right">{t('watchlist.currentPrice', '現價')}</TableCell>
              <TableCell align="right">{t('watchlist.change', '漲跌')}</TableCell>
              <TableCell align="right">{t('watchlist.changePercent', '漲跌幅')}</TableCell>
              <TableCell align="center">{t('watchlist.actions', '操作')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            <TableRow>
              <TableCell colSpan={6} align="center">
                <Typography variant="body2" color="text.secondary">
                  {t('watchlist.noStocks', '暫無股票資料')}
                </Typography>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default Watchlist;