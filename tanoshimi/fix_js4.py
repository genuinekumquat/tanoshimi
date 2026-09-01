import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace(');\\n    function initLive2D() {', ');\\n    async function initLive2D() {')
text = text.replace(');\\n    async async function', ');\\n    async function') # just in case

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)