import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  List,
  ListItem,
  ListItemText,
  Chip,
  Box,
  IconButton,
} from '@mui/material';
import { Security, Close } from '@mui/icons-material';
import authService, { SecurityEvent } from '../../services/authService';

interface SecurityMonitorProps {
  open: boolean;
  onClose: () => void;
}

const SecurityMonitor: React.FC<SecurityMonitorProps> = ({ open, onClose }) => {
  const [events, setEvents] = useState<SecurityEvent[]>([]);

  useEffect(() => {
    if (open) {
      const securityEvents = authService.getSecurityEvents();
      setEvents(securityEvents.reverse()); // Show newest first
    }
  }, [open]);

  const getEventColor = (type: SecurityEvent['type']) => {
    switch (type) {
      case 'LOGIN_SUCCESS':
        return 'success';
      case 'LOGIN_FAILURE':
        return 'error';
      case 'SUSPICIOUS_ACTIVITY':
        return 'warning';
      case 'TOKEN_REFRESH':
        return 'info';
      case 'LOGOUT':
        return 'default';
      default:
        return 'default';
    }
  };

  const formatEventType = (type: SecurityEvent['type']) => {
    return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
  };

  const handleClearEvents = () => {
    authService.clearSecurityEvents();
    setEvents([]);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Box display="flex" alignItems="center" gap={1}>
            <Security />
            <Typography variant="h6">Security Events Monitor</Typography>
          </Box>
          <IconButton onClick={onClose} size="small">
            <Close />
          </IconButton>
        </Box>
      </DialogTitle>
      
      <DialogContent>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          This monitor shows authentication and security events for debugging purposes.
          In production, these events would be sent to a security monitoring service.
        </Typography>
        
        {events.length === 0 ? (
          <Typography variant="body1" color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
            No security events recorded
          </Typography>
        ) : (
          <List>
            {events.map((event, index) => (
              <ListItem key={index} divider>
                <ListItemText
                  primary={
                    <Box display="flex" alignItems="center" gap={1} mb={1}>
                      <Chip
                        label={formatEventType(event.type)}
                        color={getEventColor(event.type) as any}
                        size="small"
                      />
                      <Typography variant="caption" color="text.secondary">
                        {new Date(event.timestamp).toLocaleString()}
                      </Typography>
                    </Box>
                  }
                  secondary={
                    <Box>
                      {event.details && (
                        <Typography variant="body2" component="pre" sx={{ fontSize: '0.75rem', mt: 1 }}>
                          {JSON.stringify(event.details, null, 2)}
                        </Typography>
                      )}
                      <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                        User Agent: {event.userAgent.substring(0, 100)}...
                      </Typography>
                    </Box>
                  }
                />
              </ListItem>
            ))}
          </List>
        )}
      </DialogContent>
      
      <DialogActions>
        <Button onClick={handleClearEvents} color="warning">
          Clear Events
        </Button>
        <Button onClick={onClose} variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default SecurityMonitor;