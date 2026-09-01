path_js = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/planner.js'
path_build_js = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/static/js/planner.js'
with open(path_js, 'r', encoding='utf-8') as f:
    text_js = f.read()

import re
text_js = re.sub(r"document\.getElementById\('btn-pay'\)\?\.addEventListener\('click', async \(\) => \{.+?\}\);", "", text_js, flags=re.DOTALL)

with open(path_js, 'w', encoding='utf-8') as f:
    f.write(text_js)
with open(path_build_js, 'w', encoding='utf-8') as f:
    f.write(text_js)