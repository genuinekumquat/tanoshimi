import codecs

with codecs.open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'r', 'utf-8-sig') as f:
    text = f.read()

text = text.replace('if (!schedule.isDraft() && !isCustom) {', 'if (false) {')

with codecs.open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'w', 'utf-8') as f:
    f.write(text)
