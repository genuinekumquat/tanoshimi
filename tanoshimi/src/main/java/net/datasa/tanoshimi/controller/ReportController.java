package net.datasa.tanoshimi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.ReportRequest;
import net.datasa.tanoshimi.domain.entity.ReportTargetType;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.ReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/** 게시글/파티/사용자 공용 신고 접수. */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final UserRepository userRepository;
    private final ReportService reportService;

    @PostMapping("/api/reports")
    @ResponseBody
    public ApiResponse<Void> submit(@Valid @RequestBody ReportRequest request,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity reporter = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        reportService.submit(reporter, ReportTargetType.valueOf(request.targetType()),
                request.targetId(), request.targetLabel(), request.reason());
        return ApiResponse.okMessage("신고가 접수되었습니다. 검토 후 조치하겠습니다.");
    }
}
