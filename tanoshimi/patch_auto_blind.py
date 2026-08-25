import re

path = 'src/main/java/net/datasa/tanoshimi/service/ReportService.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# Add imports
text = text.replace('import java.util.List;', '''import java.util.List;
import net.datasa.tanoshimi.repository.PostRepository;
import net.datasa.tanoshimi.repository.PartyRepository;
import net.datasa.tanoshimi.domain.entity.PostEntity;
import net.datasa.tanoshimi.domain.entity.PartyEntity;''')

# Add repositories
text = text.replace('private final NotificationRepository notificationRepository;', '''private final NotificationRepository notificationRepository;
    private final PostRepository postRepository;
    private final PartyRepository partyRepository;''')

# Add auto-blind logic
target = 'for (UserEntity admin : admins) {'
auto_blind = '''
        long totalReports = reportRepository.countByTargetTypeAndTargetId(targetType, targetId);
        if (totalReports >= 3) {
            String blindMsg = "";
            if (targetType == ReportTargetType.post) {
                postRepository.findById(targetId).ifPresent(p -> { p.blind(); postRepository.save(p); });
                blindMsg = "(시스템: 누적 3회 이상 접수되어 해당 게시글이 자동 블라인드 되었습니다.)";
            } else if (targetType == ReportTargetType.party) {
                partyRepository.findById(targetId).ifPresent(p -> { p.blind(); partyRepository.save(p); });
                blindMsg = "(시스템: 누적 3회 이상 접수되어 해당 파티가 자동 블라인드 되었습니다.)";
            }
            
            for (UserEntity admin : admins) {
                notificationRepository.save(new NotificationEntity(admin, "SYSTEM", "자동 블라인드 조치", "'" + targetLabel + "' 관련 " + blindMsg, linkUrl));
            }
        }

        for (UserEntity admin : admins) {'''

text = text.replace(target, auto_blind)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
print("Auto-blind logic integrated into ReportService!")
