import re
with open('src/main/resources/templates/party/detail.html', 'r', encoding='utf-8') as f:
    html = f.read()

html = html.replace('/api/parties//apply', '/api/parties/${partyId}/apply')
html = html.replace('/api/party/${partyId}/apply', '/api/parties/${partyId}/apply')

with open('src/main/resources/templates/party/detail.html', 'w', encoding='utf-8') as f:
    f.write(html)
