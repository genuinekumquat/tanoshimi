import re

html_path_src = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/fragments/layout.html'
html_path_build = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/templates/fragments/layout.html'

with open(html_path_src, 'r', encoding='utf-8') as f:
    text = f.read()

# Update bubble styling
target_bubble = '<div id="companion-speech-bubble"'
# Find the end of this div line
end_bubble = text.find('</div>', text.find(target_bubble)) + 6
old_bubble_line = text[text.find(target_bubble):end_bubble]

new_bubble_line = '<div id="companion-speech-bubble" style="display: none; position: absolute; right: calc(200px * var(--vivian-scale, 1)); bottom: calc(230px * var(--vivian-scale, 1)); transform: scale(var(--vivian-scale, 1)); transform-origin: bottom right; background: #fdf5ff; padding: 14px 20px; border-radius: 20px; border: 2px solid #d4a9f0; color: #5a3c6a; box-shadow: 0 8px 24px rgba(0,0,0,0.15); width: max-content; max-width: 250px; font-size: 14px; font-weight: 700; z-index: 10001; word-break: keep-all; pointer-events: auto;"></div>'

text = text.replace(old_bubble_line, new_bubble_line)
text = re.sub(r'companion-widget\.js\?v=\d+', 'companion-widget.js?v=36', text)

with open(html_path_src, 'w', encoding='utf-8') as f:
    f.write(text)
with open(html_path_build, 'w', encoding='utf-8') as f:
    f.write(text)