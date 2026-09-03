package net.datasa.tanoshimi.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * [vanity-url 신규] "/{username}" 프로필 주소(예: /yuja) 전용. 담당: 김민규(⑥).
 *
 * <p><b>스프링 라우팅 우선순위(왜 이 매핑이 다른 라우트를 절대 가리지 않는가)</b>: 같은
 * 요청 경로에 대해 스프링 MVC 는 "리터럴(고정 문자열) 세그먼트"를 "{변수} 세그먼트"보다
 * 항상 더 구체적인 매칭으로 취급해서 먼저 고른다(PathPattern 비교 규칙 - 고정 세그먼트가
 * 변수 세그먼트를 이긴다). 그래서 "GET /mypage", "GET /login", "GET /board" 처럼 이미 있는
 * 한-세그먼트짜리 리터럴 경로들은 항상 그 컨트롤러가 먼저 잡고, 이 클래스의
 * "GET /{username}" 은 그 어떤 리터럴 매핑과도 일치하지 않는 "나머지 한 세그먼트짜리
 * 경로"만 받는다. "/board/list", "/api/auth/signup" 처럼 세그먼트가 2개 이상인 경로는
 * 애초에 "/{username}"(정확히 1세그먼트) 패턴과 매칭 대상 자체가 아니라서 경합할 일이
 * 없다 - 그래도 사람이 보기에 헷갈리는 걸 막으려고(예: 아이디가 "board" 면 "/board" 는
 * {username}으로 잡히는데 "/board/list" 는 여전히 게시판으로 잡혀서 뭔가 이상해 보임)
 * UsernamePolicy 의 예약어 목록에는 다중 세그먼트 경로의 첫 세그먼트까지 전부 넣어뒀다.
 *
 * <p>이 매핑을 MyPageController 에 얹지 않고 별도 클래스로 뺀 이유: "/mypage/..." 류의
 * 다중 세그먼트 매핑들 사이에 "/{username}" 같은 한 세그먼트짜리 캐치올성 매핑을 섞어두면
 * 코드를 읽을 때 매핑 우선순위를 스프링 내부 규칙까지 알아야 안심할 수 있다 - 클래스를
 * 분리해두면 "이 컨트롤러는 정확히 한 세그먼트짜리 사용자 지정 경로만 담당한다"는 게
 * 파일 구조만 봐도 드러난다.
 */
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final MyPageController myPageController;

    @GetMapping("/{username}")
    public String vanityProfile(@PathVariable String username,
                                @AuthenticationPrincipal CustomUserDetails principal,
                                Model model, HttpServletResponse response) {
        // 형식 검증(예약어는 "발급을 막는" 규칙일 뿐 조회를 막지 않음) + DB 조회는 서비스에서.
        Optional<UserEntity> found = userService.findByVanityUsername(username);

        if (found.isEmpty()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("message", "존재하지 않는 사용자입니다.");
            return "error/404";
        }

        UserEntity target = found.get();

        // [설계 제약 #1] 본인 소유 vanity URL 이면 기존 /mypage(자기 대시보드, 수정 권한 전부)
        // 로 그대로 보낸다 - 이 컨트롤러는 "본인이 아닐 때"만 실제로 화면을 그린다.
        boolean isOwner = principal != null && principal.getId().equals(target.getId());
        if (isOwner) {
            return "redirect:/mypage";
        }

        // [설계 제약 #2/#3] 그 외에는 "/users/{id}" 가 이미 쓰는 렌더링 로직(비공개 블러 처리,
        // 팔로우/DM 버튼 포함)을 그대로 재사용한다 - MyPageController.renderProfile 참고.
        return myPageController.renderProfile(target, principal, model);
    }
}
