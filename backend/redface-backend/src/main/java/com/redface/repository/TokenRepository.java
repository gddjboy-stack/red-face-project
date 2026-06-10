package com.redface.repository;

import com.redface.model.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {
    boolean existsByTokenId(String tokenId);
    List<Token> findByAqisoBatchId(String aqisoBatchId);
}
