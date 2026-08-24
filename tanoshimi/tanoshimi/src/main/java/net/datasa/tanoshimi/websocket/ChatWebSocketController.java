package net.datasa.tanoshimi.websocket;

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ChatMessageView;
import net.datasa.tanoshimi.domain.dto.ChatSendRequest;
import net.datasa.tanoshimi.domain.entity.ChatRoomEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.ChatRoomRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    @MessageMapping("/chat.send/{roomId}")
    @SendTo("/topic/chat/{roomId}")
    public ChatMessageView send(@DestinationVariable Long roomId, ChatSendRequest request, Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("로그인 세션이 없습니다.");
        }

        Long userId;
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            userId = userDetails.getId();
        } else {
            try {
                userId = Long.parseLong(principal.getName());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("사용자 인증 정보 형식이 올바르지 않습니다.");
            }
        }

        ChatRoomEntity room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        UserEntity sender = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return chatService.send(room, sender, request.content());
    }
}
