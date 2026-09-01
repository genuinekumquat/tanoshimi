package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.ChatMessageView;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.*;
import net.datasa.tanoshimi.service.BlockService;
import net.datasa.tanoshimi.service.ChatService;
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
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatService chatService;
    private final BlockService blockService;

    /** 상대방과의 DM 방을 찾거나 없으면 새로 만든다. */
    @PostMapping("/{targetId}/room")
    public ApiResponse<Long> openRoom(@PathVariable Long targetId, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserEntity target = userRepository.findById(targetId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 기존 방은 그대로 두되(히스토리 열람은 유지), 차단 관계면 메시지 전송은 ChatService.send()에서 막힌다.
        // 여기서는 차단 상태에서 "새 방"이 새로 열리는 것만 막는다.
        if (blockService.isBlockedEitherWay(me, target)) {
            throw new BusinessException(ErrorCode.BLOCKED_USER);
        }

        List<ChatRoomMemberEntity> myRooms = chatRoomMemberRepository.findByUser(me);
        for (ChatRoomMemberEntity myMembership : myRooms) {
            ChatRoomEntity room = myMembership.getRoom();
            if (room.getType() == ChatRoomType.dm && chatRoomMemberRepository.existsByRoomAndUser(room, target)) {
                return ApiResponse.ok(room.getId());
            }
        }

        ChatRoomEntity room = chatRoomRepository.save(ChatRoomEntity.forDm());
        chatRoomMemberRepository.save(new ChatRoomMemberEntity(room, me));
        chatRoomMemberRepository.save(new ChatRoomMemberEntity(room, target));
        return ApiResponse.ok(room.getId());
    }

    @GetMapping("/rooms/{roomId}/history")
    public ApiResponse<List<ChatMessageView>> history(@PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails principal) {
        ChatRoomEntity room = chatRoomRepository.findById(roomId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        chatService.assertMember(room, me);
        return ApiResponse.ok(chatService.history(room));
    }
}
