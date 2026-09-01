package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 칭호 마스터. v17 부터 38종 8카테고리 (첫 발자국, 간사이 마스터 등). */
@Entity
@Getter
@Table(name = "titles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TitleEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    /** 칭호 카테고리(표시용). 분류는 code 접두사와 같다 - TitleService 주석 참고. */
    @Column(length = 30)
    private String category;

    @Column(name = "condition_desc", length = 200)
    private String conditionDesc;

    @Column(name = "icon_key", length = 50)
    private String iconKey;
}
