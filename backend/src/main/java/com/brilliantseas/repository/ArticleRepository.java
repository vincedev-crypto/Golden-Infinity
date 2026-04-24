package com.brilliantseas.repository;

import com.brilliantseas.domain.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID> {
    Optional<Article> findBySlugAndDeletedAtIsNull(String slug);
    Page<Article> findByIsPublishedTrueAndDeletedAtIsNullOrderByPublishedDtDesc(Pageable pageable);
    Page<Article> findByCategoryAndIsPublishedTrueAndDeletedAtIsNullOrderByPublishedDtDesc(String category, Pageable pageable);
}
