package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.ChatRoomEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ChatRoomRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.ChatService;
import net.datasa.tanoshimi.service.DmService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** 개인 간(1:1) 쪽지함 화면. 실제 송수신은 ChatWebSocketController(STOMP) 가 담당한다. */
@Controller
@RequiredArgsConstructor
public class MessagesController {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final DmService dmService;
    private final ChatService chatService;

    @GetMapping("/messages")
    public String list(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        model.addAttribute("rooms", dmService.listMyRooms(me));
        return "messages/list";
    }

    @GetMapping("/messages/{roomId}")
    public String room(@PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        ChatRoomEntity room = chatRoomRepository.findById(roomId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        dmService.assertMember(room, me);

        model.addAttribute("roomId", room.getId());
        model.addAttribute("otherUserName", dmService.otherUserName(room, me));
        model.addAttribute("chatHistory", chatService.history(room));
        return "messages/room";
    }
}
