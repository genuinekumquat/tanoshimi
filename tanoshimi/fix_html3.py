import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/fragments/layout.html', 'r', encoding='utf-8') as f:
    text = f.read()

new_block = '''    <!-- 2. 캐릭터 캔버스 -->
    <button id="companion-chat-open" style="pointer-events: auto; position: fixed; right: 40px; bottom: 40px; padding: 12px 24px; background: var(--forest, #4A6741); color: white; border: 2px solid white; border-radius: 30px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 16px rgba(0,0,0,0.3); font-size: 15px; transition: all 0.2s; z-index: 10000;">💬 대화하기</button>
    <div id="companion-character-wrap" style="position: fixed; right: 180px; bottom: 20px; display: flex; flex-direction: column; justify-content: flex-end; align-items: flex-end; pointer-events: none; z-index:9999;">
        <canvas id="companion-canvas" style="pointer-events: auto; cursor: grab; filter: drop-shadow(0px 4px 6px rgba(0,0,0,0.2)); transition: filter 0.2s;"></canvas>
    </div>
'''

# Find the start index
start_idx = text.find('<!-- 2. 캐릭터 캔버스 -->')
# Find the end index of the div block matching it.
# We know it ends before '<script src="/js/companion-widget.js?v=21"></script>' (or similar)
end_idx = text.find('<script src="/js/companion-widget.js', start_idx)

if start_idx != -1 and end_idx != -1:
    text = text[:start_idx] + new_block + '\n    ' + text[end_idx:]
else:
    print('Failed to find block!')

text = re.sub(r'companion-widget\.js\?v=\d+', 'companion-widget.js?v=25', text)

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/fragments/layout.html', 'w', encoding='utf-8') as f:
    f.write(text)