import codecs
import re
with codecs.open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'r', 'utf-8-sig') as f:
    text = f.read()

# For resizeItem and removeItem, just comment out or remove the isDraft check
# The package_default check already exists right before it.
text = re.sub(r"if \(!item\.getSchedule\(\)\.isDraft\(\).*?\{.*?\}", "", text, flags=re.DOTALL)

with codecs.open('src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java', 'w', 'utf-8') as f:
    f.write(text)
