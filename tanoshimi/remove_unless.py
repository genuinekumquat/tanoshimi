with open('src/main/resources/templates/board/list.html', 'r', encoding='utf-8') as f:
    text = f.read()
target1 = 'th:unless="${post.user.id == #authentication.principal.id}" '
text = text.replace(target1, '')
with open('src/main/resources/templates/board/list.html', 'w', encoding='utf-8') as f:
    f.write(text)

with open('src/main/resources/templates/party/board.html', 'r', encoding='utf-8') as f:
    text = f.read()
target2 = 'th:unless="${party.owner.id == #authentication.principal.id}" '
text = text.replace(target2, '')
with open('src/main/resources/templates/party/board.html', 'w', encoding='utf-8') as f:
    f.write(text)

print("th:unless removed!")