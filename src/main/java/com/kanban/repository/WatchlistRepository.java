package com.kanban.repository;

import com.kanban.domain.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, String> {
    
    List<Watchlist> findByUserId(String userId);
    
    Optional<Watchlist> findByUserIdAndId(String userId, String watchlistId);
    
    @Query("SELECT COUNT(c) FROM Card c WHERE c.watchlist.id = :watchlistId")
    long countCardsByWatchlistId(@Param("watchlistId") String watchlistId);
    
    boolean existsByUserIdAndName(String userId, String name);
}