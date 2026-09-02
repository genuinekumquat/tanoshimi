package net.datasa.tanoshimi.domain.dto;

import java.util.List;

/** 프론트엔드가 매 요청마다 대화 이력 전체 + 새 메시지를 함께 보낸다(무상태 서버). */
public record CompanionChatRequest(List<CompanionChatTurn> history, String message) {
}
