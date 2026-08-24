import codecs

with codecs.open('src/main/resources/templates/planner/index.html', 'r', 'utf-8-sig') as f:
    text = f.read()

text = text.replace('src="/js/planner.js?v=2"', 'src="/js/planner.js?v=3"')

with codecs.open('src/main/resources/templates/planner/index.html', 'w', 'utf-8') as f:
    f.write(text)
