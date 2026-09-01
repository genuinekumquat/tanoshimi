path = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/planner/index.html'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

import re
old_text = "color:var(--ink-soft); border:2px dashed var(--rope); box-shadow:none;"
new_text = "color:var(--ink-soft); box-shadow:none;"
text = text.replace(old_text, new_text)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

path2 = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/templates/planner/index.html'
with open(path2, 'w', encoding='utf-8') as f:
    f.write(text)