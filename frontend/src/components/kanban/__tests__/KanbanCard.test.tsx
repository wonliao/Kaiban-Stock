import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { DragDropContext, Droppable } from 'react-beautiful-dnd';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../i18n';
import KanbanCard from '../KanbanCard';
import kanbanSlice, { Card } from '../../../store/slices/kanbanSlice';

// Mock store
const mockStore = configureStore({
  reducer: {
    kanban: kanbanSlice,
  },
});

// Mock card data
const mockCard: Card = {
  id: '1',
  stockCode: '2330',
  stockName: '台積電',
  status: 'watch',
  currentPrice: 580.00,
  changePercent: 2.5,
  volume: 25000000,
  ma20: 575.00,
  rsi: 65.5,
  note: '等待突破前高',
  createdAt: '2024-01-15T09:00:00Z',
  updatedAt: '2024-01-15T14:30:00Z',
};

const TestWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <Provider store={mockStore}>
    <I18nextProvider i18n={i18n}>
      <DragDropContext onDragEnd={() => {}}>
        <Droppable droppableId="test">
          {(provided) => (
            <div ref={provided.innerRef} {...provided.droppableProps}>
              {children}
              {provided.placeholder}
            </div>
          )}
        </Droppable>
      </DragDropContext>
    </I18nextProvider>
  </Provider>
);

describe('KanbanCard', () => {
  const mockOnCardClick = jest.fn();

  beforeEach(() => {
    mockOnCardClick.mockClear();
  });

  it('renders card with stock information', () => {
    render(
      <TestWrapper>
        <KanbanCard card={mockCard} index={0} onCardClick={mockOnCardClick} />
      </TestWrapper>
    );

    expect(screen.getByText('2330')).toBeInTheDocument();
    expect(screen.getByText('台積電')).toBeInTheDocument();
    expect(screen.getByText('$580.00')).toBeInTheDocument();
    expect(screen.getByText('+2.50%')).toBeInTheDocument();
  });

  it('displays technical indicators', () => {
    render(
      <TestWrapper>
        <KanbanCard card={mockCard} index={0} onCardClick={mockOnCardClick} />
      </TestWrapper>
    );

    expect(screen.getByText('MA20: $575.00')).toBeInTheDocument();
    expect(screen.getByText('RSI: 65.5')).toBeInTheDocument();
  });

  it('calls onCardClick when card is clicked', () => {
    render(
      <TestWrapper>
        <KanbanCard card={mockCard} index={0} onCardClick={mockOnCardClick} />
      </TestWrapper>
    );

    fireEvent.click(screen.getByText('2330'));
    expect(mockOnCardClick).toHaveBeenCalledWith(mockCard);
  });

  it('displays note when present', () => {
    render(
      <TestWrapper>
        <KanbanCard card={mockCard} index={0} onCardClick={mockOnCardClick} />
      </TestWrapper>
    );

    expect(screen.getByText('等待突破前高')).toBeInTheDocument();
  });

  it('formats volume correctly', () => {
    render(
      <TestWrapper>
        <KanbanCard card={mockCard} index={0} onCardClick={mockOnCardClick} />
      </TestWrapper>
    );

    expect(screen.getByText(/25.0M/)).toBeInTheDocument();
  });
});