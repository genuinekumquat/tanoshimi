import re

with open('src/main/java/net/datasa/tanoshimi/service/ReportService.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Add imports
text = text.replace('import org.springframework.transaction.annotation.Transactional;', '''import org.springframework.transaction.annotation.Transactional;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.repository.NotificationRepository;
import net.datasa.tanoshimi.domain.entity.Role;
import net.datasa.tanoshimi.domain.entity.NotificationEntity;
import java.util.List;''')

# Add repositories
text = text.replace('private final ReportRepository reportRepository;', '''private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;''')

# Add logic inside submit
submit_old = "reportRepository.save(new ReportEntity(reporter, targetType, targetId, targetLabel, reason.trim()));"
submit_new = '''reportRepository.save(new ReportEntity(reporter, targetType, targetId, targetLabel, reason.trim()));
        
        String linkUrl = "";
        if (targetType == ReportTargetType.post) {
            linkUrl = "/board/" + targetId;
        } else if (targetType == ReportTargetType.party) {
            linkUrl = "/party-board/" + targetId;
        }
        
        List<UserEntity> admins = userRepository.findByRole(Role.admin);
        for (UserEntity admin : admins) {
            notificationRepository.save(new NotificationEntity(admin, "REPORT", "새로운 신고 접수", "'" + targetLabel + "'에 대한 신고가 접수되었습니다.", linkUrl));
        }'''
text = text.replace(submit_old, submit_new)

with open('src/main/java/net/datasa/tanoshimi/service/ReportService.java', 'w', encoding='utf-8') as f:
    f.write(text)
print("Patched ReportService")