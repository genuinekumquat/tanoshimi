import codecs

with codecs.open('src/main/resources/templates/planner/index.html', 'r', 'utf-8-sig') as f:
    text = f.read()

text = text.replace('<script src="/js/planner.js"></script>', '<script src="/js/planner.js?v=2"></script>')

with codecs.open('src/main/resources/templates/planner/index.html', 'w', 'utf-8') as f:
    f.write(text)
