import codecs

with codecs.open('src/main/resources/templates/planner/index.html', 'r', 'utf-8-sig') as f:
    text = f.read()

text = text.replace('th:src="@{/js/planner.js}"', 'th:src="@{/js/planner.js?v=4}"')

with codecs.open('src/main/resources/templates/planner/index.html', 'w', 'utf-8') as f:
    f.write(text)
