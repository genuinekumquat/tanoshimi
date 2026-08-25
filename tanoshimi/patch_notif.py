import re

def patch_notification_service():
    path = 'src/main/java/net/datasa/tanoshimi/service/NotificationService.java'
    with open(path, 'r', encoding='utf-8') as f:
        text = f.read()

    text = text.replace('import org.springframework.transaction.annotation.Transactional;', 
'''import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;''')

    text = text.replace('private final NotificationRepository notificationRepository;',
'''private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;''')

    text = text.replace('notificationRepository.save(new NotificationEntity(user, type, title, message, linkUrl));',
'''NotificationEntity notif = notificationRepository.save(new NotificationEntity(user, type, title, message, linkUrl));
        try {
            NotificationView view = new NotificationView(
                    notif.getId(), notif.getType(), notif.getTitle(), notif.getMessage(),
                    notif.getLinkUrl(), notif.isRead(), notif.getCreatedAt() != null ? notif.getCreatedAt().format(TIME_FMT) : ""
            );
            messagingTemplate.convertAndSend("/topic/user." + user.getId() + ".notifications", view);
        } catch(Exception e) {
            // Ignore messaging errors
        }''')
    
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)

patch_notification_service()
print("Patched NotificationService.java")