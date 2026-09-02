package net.datasa.tanoshimi.controller;

import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.entity.VerificationPurpose;
import net.datasa.tanoshimi.service.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** send/confirm 이 정규화(trim+lowercase)된 이메일로 서비스에 위임되는지, 목적(purpose)이 항상 signup으로 고정되는지 확인한다. */
@ExtendWith(MockitoExtension.class)
class EmailVerificationApiControllerTest {

    @Mock
    private EmailVerificationService emailVerificationService;

    private EmailVerificationApiController controller;

    @BeforeEach
    void setUp() {
        controller = new EmailVerificationApiController(emailVerificationService);
    }

    @Test
    void send는_이메일을_정규화해서_서비스에_전달한다() {
        ApiResponse<Void> result = controller.send(new EmailVerificationApiController.SendReq("  User@Test.com "));

        verify(emailVerificationService).sendCode("user@test.com", VerificationPurpose.signup);
        assertThat(result.success()).isTrue();
    }

    @Test
    void confirm은_이메일을_정규화해서_서비스에_전달한다() {
        ApiResponse<Void> result = controller.confirm(
                new EmailVerificationApiController.ConfirmReq("  User@Test.com ", "123456"));

        verify(emailVerificationService).confirmCode("user@test.com", "123456", VerificationPurpose.signup);
        assertThat(result.success()).isTrue();
    }
}
