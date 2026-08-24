package net.datasa.tanoshimi.domain.dto;

public record ChatMessageView(
        Long id, Long roomId, Long senderId, String senderName,
        String content, String originalLang, String createdAt
) {
}
