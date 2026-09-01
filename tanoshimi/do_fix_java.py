import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/service/ChatbotActivityService.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace the specific malformed strings using exact matches
text = text.replace('Desc:%s\n"', 'Desc:%s\\n"')

text = text.replace('if relevant:\n%s\n', 'if relevant:\\n%s\\n')

text = text.replace('items with kind: "custom".\n', 'items with kind: "custom".\\n')
text = text.replace('json schema:\n', 'json schema:\\n')
text = text.replace('}]\n"', '}]\\n"')

temp_text = text
# Also check if it used text blocks incorrectly
# It looks like it just literally printed raw newlines inside "..."
# I will use a simple regex to fix any "...\n..." inside String.format if it's not a text block """

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/service/ChatbotActivityService.java', 'w', encoding='utf-8') as f:
    f.write(text)