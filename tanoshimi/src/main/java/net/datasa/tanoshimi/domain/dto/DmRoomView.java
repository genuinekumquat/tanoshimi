package net.datasa.tanoshimi.domain.dto;

/** 내 쪽지함(DM 목록) 화면용 뷰. */
public record DmRoomView(
        Long roomId, Long otherUserId, String otherUserName,
        String lastMessagePreview, String lastMessageTime
) {
}
