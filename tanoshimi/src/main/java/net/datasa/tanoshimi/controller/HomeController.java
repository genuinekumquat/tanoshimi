package net.datasa.tanoshimi.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.BannerEntity;
import net.datasa.tanoshimi.repository.BannerRepository;
import net.datasa.tanoshimi.service.PartyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PartyService partyService;
    private final BannerRepository bannerRepository;

    @GetMapping("/")
    public String home(Model model) {
        List<BannerEntity> banners = bannerRepository.findAllByOrderBySortOrderAsc();

        model.addAttribute("urgentParties", partyService.urgentPartyCards());
        model.addAttribute("banners", banners);
        return "index";
    }
}
