import os
import re

def add_onclick(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        text = f.read()

    # Find the report buttons we just added and add onclick
    target = 'th:attr="data-report-type'
    replacement = 'onclick="event.preventDefault(); event.stopPropagation();" th:attr="data-report-type'
    
    if replacement not in text:
        text = text.replace(target, replacement)
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(text)

add_onclick('src/main/resources/templates/board/list.html')
add_onclick('src/main/resources/templates/party/board.html')
print("Added onclick to buttons")