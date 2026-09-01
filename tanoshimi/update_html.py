import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/fragments/layout.html', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Add Speech Bubble to character wrap
target_wrap = '<div id="companion-character-wrap"'
new_wrap = '''    <div id="companion-character-wrap" style="position: fixed; right: 180px; bottom: 20px; display: flex; flex-direction: column; justify-content: flex-end; align-items: flex-end; pointer-events: none; z-index:9999;">
        <div id="companion-speech-bubble" style="display: none; position: absolute; right: 110%; bottom: 85%; background: white; padding: 14px 20px; border-radius: 24px; border-bottom-right-radius: 4px; box-shadow: 0 8px 24px rgba(0,0,0,0.2); width: max-content; max-width: 320px; font-size: 14px; font-weight: bold; color: #333; z-index: 10001; word-break: keep-all; pointer-events: auto;"></div>
        <canvas id="companion-canvas" style="pointer-events: auto; cursor: grab; filter: drop-shadow(0px 4px 6px rgba(0,0,0,0.2)); transition: filter 0.2s;"></canvas>
    </div>'''
text = re.sub(r'<div id="companion-character-wrap".*?</canvas>\s*</div>', new_wrap, text, flags=re.DOTALL)

# 2. Add Toggle Checkbox to control bar
target_btn = '👻 숨기기</button>'
new_btn = '👻 숨기기</button>\n                      <div style="width: 1px; background: #ccc; margin: 0 4px;"></div>\n                      <label style="cursor:pointer; display:flex; align-items:center; gap:4px; font-weight:bold; color:var(--forest, #4A6741); margin-left:auto;"><input type="checkbox" id="companion-show-bot-chat"> 채팅방 출력 ✏️</label>'
text = text.replace(target_btn, new_btn)

# bump cache
text = re.sub(r'companion-widget\.js\?v=\d+', 'companion-widget.js?v=30', text)

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/fragments/layout.html', 'w', encoding='utf-8') as f:
    f.write(text)