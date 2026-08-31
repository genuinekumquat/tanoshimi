package net.datasa.tanoshimi.domain.dto;

/** 마스코트 챗봇 대화 한 턴. role 은 "user" 또는 "assistant". 서버는 이력을 저장하지 않는다. */
public record CompanionChatTurn(String role, String content) {
}
