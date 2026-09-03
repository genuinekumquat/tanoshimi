package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.service.BannerService;
import net.datasa.tanoshimi.service.PartyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PartyService partyService;
    private final BannerService bannerService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("urgentParties", partyService.urgentPartyCards());
        model.addAttribute("banners", bannerService.list());
        return "index";
    }
}
