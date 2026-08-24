package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "banners")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BannerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String imageUrl;
    
    @Column
    private String targetUrl;

    @Column(name = "sort_order")
    private int sortOrder;
}