package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.ChatMessageView;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.ChatService;
import net.datasa.tanoshimi.service.DmService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 간(1:1) 실시간 채팅방 개설.
 * 파티 채팅과 같은 chat_rooms/chat_messages 구조를 공유하며 type=dm 으로 구분한다.
 * 실제 메시지 송수신은 ChatWebSocketController(STOMP) 를 그대로 재사용한다.
 */
@RestController
@RequestMapping("/api/dm")
@RequiredArgsConstructor
public class DmChatController {

    private final UserRepository userRepository;
    private final ChatService chatService;
    private final DmService dmService;

    /** 상대방과의 DM 방을 찾거나 없으면 새로 만든다. */
    @PostMapping("/{targetId}/room")
    public ApiResponse<Long> openRoom(@PathVariable Long targetId, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ApiResponse.ok(dmService.openOrGetRoomWith(me, targetId));
    }

    @GetMapping("/rooms/{roomId}/history")
    public ApiResponse<List<ChatMessageView>> history(@PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails principal) {
        ChatRoomEntity room = dmService.getRoom(roomId);
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        chatService.assertMember(room, me);
        return ApiResponse.ok(chatService.history(room));
    }
}
