package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.repository.TourRepository;
import net.datasa.tanoshimi.repository.TripScheduleRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class VivianQueryService {
    private final UserRepository userRepository;
    private final TourRepository tourRepository;
    private final TripScheduleRepository tripScheduleRepository;

    public Map<String, Object> getSiteStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalTours", tourRepository.count());
        stats.put("totalTripSchedules", tripScheduleRepository.count());
        return stats;
    }

    public Map<String, Object> searchTours(String keyword) {
        Map<String, Object> result = new HashMap<>();
        var list = tourRepository.findByTitleContainingIgnoreCase(keyword).stream()
                .limit(5) // Limit just in case
                .map(t -> Map.of("id", t.getId(), "title", t.getTitle(), "desc", t.getDescription()))
                .toList();
        result.put("tours", list);
        return result;
    }
}
