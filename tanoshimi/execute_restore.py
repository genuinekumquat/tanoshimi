import json
import re

with open('restore_detail.py', 'r', encoding='utf-8') as f:
    text = f.read()

# isolate the raw_json string
start = text.find('r"""') + 4
end = text.rfind('"""')
raw_json = text[start:end]

data = json.loads(raw_json)
html = data["result"]
html = html.replace('/api/party/${partyId}/apply', '/api/parties/${partyId}/apply')

def replace_unicode(match):
    s = match.group(0)
    return json.loads(f'"{s}"')

html = re.sub(r'(\\u[0-9a-fA-F]{4})+', replace_unicode, html)

with open('src/main/resources/templates/party/detail.html', 'w', encoding='utf-8') as f:
    f.write(html)
print("detail.html restored and decoded completely!")
