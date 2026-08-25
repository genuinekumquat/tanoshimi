package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(length = 255)
    private String region;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private String authorId; // User ID / username

    @Column(nullable = false)
    private int likeCount;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Recommendation(String title, String content, String region, String imageUrl, String authorId) {
        this.title = title;
        this.content = content;
        this.region = region;
        this.imageUrl = imageUrl;
        this.authorId = authorId;
        this.likeCount = 0;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void incrementLike() {
        this.likeCount++;
    }
}
