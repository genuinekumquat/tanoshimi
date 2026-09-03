package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.PartyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MyPartiesController {

    private final UserRepository userRepository;
    private final PartyService partyService;

    @GetMapping("/my-parties")
    public String myParties(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        model.addAttribute("memberships", partyService.myMemberships(me));
        return "party/my-parties";
    }
}
