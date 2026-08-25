import os

def remove_onclick(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        text = f.read()

    target = 'onclick="event.preventDefault(); event.stopPropagation();" '
    text = text.replace(target, '')
    
    # Also let's change <button to <span and </button> to </span> to be valid html inside <a>
    text = text.replace('<button type="button" th:unless', '<span th:unless')
    text = text.replace('🚨 신고</button>', '🚨 신고</span>')
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(text)

remove_onclick('src/main/resources/templates/board/list.html')
remove_onclick('src/main/resources/templates/party/board.html')
print("Undone onclick and changed to span")