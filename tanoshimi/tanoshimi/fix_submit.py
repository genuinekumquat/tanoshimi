import codecs

with codecs.open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'r', 'utf-8-sig') as f:
    text = f.read()

import re
text = re.sub(
    r'public void submitForPayment\(TripScheduleEntity schedule\) \{\s*if \(!schedule\.isDraft\(\)\) \{\s*throw new BusinessException\(ErrorCode\.SCHEDULE_NOT_DRAFT\);\s*\}',
    'public void submitForPayment(TripScheduleEntity schedule) {\n',
    text
)

# And remove it from submit() in TripScheduleEntity too, just in case
with codecs.open('src/main/java/net/datasa/tanoshimi/domain/entity/TripScheduleEntity.java', 'r', 'utf-8-sig') as f2:
    e_text = f2.read()
    
e_text = re.sub(
    r'if \(this\.status != ScheduleStatus\.draft\) \{\s*throw new IllegalStateException\("Only draft schedule can be submitted"\);\s*\}',
    '', e_text
)

with codecs.open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'w', 'utf-8') as f:
    f.write(text)

with codecs.open('src/main/java/net/datasa/tanoshimi/domain/entity/TripScheduleEntity.java', 'w', 'utf-8') as f2:
    f2.write(e_text)

