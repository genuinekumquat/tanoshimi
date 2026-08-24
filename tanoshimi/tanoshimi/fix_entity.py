with open('src/main/java/net/datasa/tanoshimi/domain/entity/TripScheduleItemEntity.java', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('@JoinColumn(name = "added_by_user_id")', '@JoinColumn(name = "added_by")')

with open('src/main/java/net/datasa/tanoshimi/domain/entity/TripScheduleItemEntity.java', 'w', encoding='utf-8') as f:
    f.write(text)
