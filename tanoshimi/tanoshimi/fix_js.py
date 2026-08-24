import codecs
with codecs.open('src/main/resources/static/js/planner.js', 'r', 'utf-8-sig') as f:
    text = f.read()

text = text.replace("el.draggable = SCHEDULE_STATUS === 'draft';", "el.draggable = item.source !== 'package_default';")
text = text.replace("SCHEDULE_STATUS === 'draft' && item.source !== 'package_default'", "item.source !== 'package_default'")

with codecs.open('src/main/resources/static/js/planner.js', 'w', 'utf-8') as f:
    f.write(text)
