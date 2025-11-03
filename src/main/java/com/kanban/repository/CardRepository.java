package com.kanban.repository;

import com.kanban.domain.entity.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, String> {
    
    Page<Card> findByUserId(String userId, Pageable pageable);
    
    Page<Card> findByUserIdAndStatus(String userId, Card.CardStatus status, Pageable pageable);
    
    @Query("SELECT c FROM Card c WHERE c.user.id = :userId " +
           "AND (LOWER(c.stockCode) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(c.stockName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(c.note) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Card> findByUserIdAndQuery(@Param("userId") String userId, 
                                   @Param("query") String query, 
                                   Pageable pageable);
    
    @Query("SELECT c FROM Card c WHERE c.user.id = :userId " +
           "AND c.status = :status " +
           "AND (LOWER(c.stockCode) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(c.stockName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(c.note) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Card> findByUserIdAndStatusAndQuery(@Param("userId") String userId,
                                            @Param("status") Card.CardStatus status,
                                            @Param("query") String query,
                                            Pageable pageable);
    
    Optional<Card> findByUserIdAndStockCode(String userId, String stockCode);
    
    boolean existsByUserIdAndStockCode(String userId, String stockCode);
    
    long countByUserId(String userId);
    
    List<Card> findByWatchlistId(String watchlistId);
    
    Optional<Card> findByIdAndUserId(String cardId, String userId);
    
    Page<Card> findByUserIdAndStatusNot(String userId, Card.CardStatus status, Pageable pageable);
    
    long countByUserIdAndStatusNot(String userId, Card.CardStatus status);
    
    long countByUserIdAndStatus(String userId, Card.CardStatus status);
}