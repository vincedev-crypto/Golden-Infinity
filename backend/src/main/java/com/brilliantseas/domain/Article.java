package com.brilliantseas.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "articles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "article_id")
    private UUID id;

    @Column(name = "slug", length = 200, unique = true, nullable = false)
    private String slug;

    @Column(name = "title", length = 300, nullable = false)
    private String title;

    // NEWS, ADVISORY, REGULATORY, COMPANY, PROMO
    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "excerpt", columnDefinition = "TEXT")
    private String excerpt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "published_dt")
    private Instant publishedDt;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
