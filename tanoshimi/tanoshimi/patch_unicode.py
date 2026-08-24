import re
import json

def replace_unicode(match):
    s = match.group(0)
    return json.loads(f'"{s}"')

try:
    with open('src/main/resources/templates/party/detail.html', 'r', encoding='utf-8') as f:
        text = f.read()

    text_decoded = re.sub(r'(\\u[0-9a-fA-F]{4})+', replace_unicode, text)
    
    with open('src/main/resources/templates/party/detail.html', 'w', encoding='utf-8') as f:
        f.write(text_decoded)
    print("detail.html patched")
except Exception as e:
    import traceback
    traceback.print_exc()
