import codecs

with codecs.open('src/main/java/net/datasa/tanoshimi/repository/ActivityRepository.java', 'r', 'utf-8-sig') as f:
    text = f.read()

if "findByStatus(" not in text:
    text = text.replace("List<ActivityEntity> findByRegionAndStatus(String region, ActiveStatus status);", 
    "List<ActivityEntity> findByRegionAndStatus(String region, ActiveStatus status);\n    List<ActivityEntity> findByStatus(ActiveStatus status);")

with codecs.open('src/main/java/net/datasa/tanoshimi/repository/ActivityRepository.java', 'w', 'utf-8') as f:
    f.write(text)
