package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 칭호 마스터 (새내기 탐험가, FLEX 마스터 등). */
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

    @Column(name = "condition_desc", length = 200)
    private String conditionDesc;

    @Column(name = "icon_key", length = 50)
    private String iconKey;
}
