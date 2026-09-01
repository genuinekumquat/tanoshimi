path = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/planner/index.html'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

import re
old_bg = "background:repeating-linear-gradient(45deg, var(--custom-slot-color, #ffa500) 0 7px, rgba(255,255,255,0.4) 7px 14px);"
new_bg = "background: var(--custom-slot-color, #ffa500);"
text = text.replace(old_bg, new_bg)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

path2 = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/templates/planner/index.html'
with open(path2, 'w', encoding='utf-8') as f:
    f.write(text)