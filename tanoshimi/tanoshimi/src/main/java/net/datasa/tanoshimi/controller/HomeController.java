package net.datasa.tanoshimi.controller;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.PartyCardView;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
import net.datasa.tanoshimi.domain.entity.BannerEntity;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.repository.PartyRepository;
import net.datasa.tanoshimi.repository.BannerRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final BannerRepository bannerRepository;

    @GetMapping("/")
    public String home(Model model) {
        List<PartyEntity> recruiting = partyRepository.findByStatusAndBlindedFalseOrderByDepartureDateAsc(PartyStatus.recruiting);

        List<PartyCardView> cards = recruiting.stream()
                .map(p -> new PartyCardView(
                        p.getId(), p.getTitle(), p.getRegion(), p.getDepartureDate().format(DATE_FMT),
                        p.getBudgetKrw(), p.getCapacity(), (int) partyMemberRepository.countByParty(p),
                        p.getThumbnailUrl(), p.getStyleTag()))
                .sorted(Comparator.<PartyCardView>comparingInt(c -> c.remaining())
                        .thenComparing(PartyCardView::departureDate))
                .toList();

        List<BannerEntity> banners = bannerRepository.findAllByOrderBySortOrderAsc();

        model.addAttribute("urgentParties", cards);
        model.addAttribute("banners", banners);
        return "index";
    }
}
