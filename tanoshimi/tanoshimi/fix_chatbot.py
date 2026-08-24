import codecs

with codecs.open('src/main/java/net/datasa/tanoshimi/service/ChatbotActivityService.java', 'r', 'utf-8-sig') as f:
    text = f.read()

fallback_code = """
        if (pool.isEmpty()) {
            pool = activityRepository.findByStatus(ActiveStatus.active);
        }
"""
text = text.replace("if (keyword == null || keyword.isBlank()) {", fallback_code + "\n        if (keyword == null || keyword.isBlank()) {")

with codecs.open('src/main/java/net/datasa/tanoshimi/service/ChatbotActivityService.java', 'w', 'utf-8') as f:
    f.write(text)
