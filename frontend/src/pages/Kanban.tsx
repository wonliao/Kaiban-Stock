import React from 'react';
import { Box } from '@mui/material';
import KanbanBoard from '../components/kanban/KanbanBoard';

const Kanban: React.FC = () => {
  return (
    <Box>
      <KanbanBoard />
    </Box>
  );
};

export default Kanban;