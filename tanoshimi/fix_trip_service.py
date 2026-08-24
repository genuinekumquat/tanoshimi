import codecs

with codecs.open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'r', 'utf-8-sig') as f:
    text = f.read()

import re
# We just wipe out the `if (!schedule.isDraft() && !isCustom)` check in addItem
text = re.sub(r'boolean isCustom = req\.activityId\(\) == null;\s*if \(!schedule\.isDraft\(\) && !isCustom\) \{\s*throw new BusinessException\(ErrorCode\.SCHEDULE_NOT_DRAFT,[^}]+\};\s*\}', 
              'boolean isCustom = req.activityId() == null;', text)

with codecs.open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'w', 'utf-8') as f:
    f.write(text)
