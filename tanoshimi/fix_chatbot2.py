import codecs

with codecs.open('src/main/java/net/datasa/tanoshimi/service/ChatbotActivityService.java', 'r', 'utf-8-sig') as f:
    text = f.read()

old_logic = """        String kw = keyword.trim();
        return pool.stream()
                .filter(a -> a.getTitle().contains(kw)
                        || (a.getStyleTag() != null && a.getStyleTag().contains(kw))
                        || (a.getDescription() != null && a.getDescription().contains(kw)))
                .limit(6)
                .toList();"""

new_logic = """        String kw = keyword.trim();
        List<ActivityEntity> results = pool.stream()
                .filter(a -> a.getTitle().contains(kw)
                        || (a.getStyleTag() != null && a.getStyleTag().contains(kw))
                        || (a.getDescription() != null && a.getDescription().contains(kw)))
                .limit(6)
                .toList();
        
        if (results.isEmpty()) {
            return pool.stream().limit(6).toList();
        }
        return results;"""

if old_logic in text:
    text = text.replace(old_logic, new_logic)
else:
    print("Warning: could not find old_logic in ChatbotActivityService.java")

with codecs.open('src/main/java/net/datasa/tanoshimi/service/ChatbotActivityService.java', 'w', 'utf-8') as f:
    f.write(text)
