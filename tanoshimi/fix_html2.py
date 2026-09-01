import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/fragments/layout.html', 'r', encoding='utf-8') as f:
    text = f.read()

target = '''    <!-- 2. 캐릭터 캔버스 -->
    <div id="companion-character-wrap" style="position: fixed; right: 20px; bottom: 20px; display: flex; flex-direction: column; justify-content: flex-end; align-items: flex-end; gap: 10px; pointer-events: none;">
        <canvas id="companion-canvas" style="pointer-events: none; filter: drop-shadow(0px 4px 6px rgba(0,0,0,0.2));"></canvas>
        <div style="display: flex; gap: 8px; pointer-events: auto; background: rgba(255,255,255,0.7); padding: 6px; border-radius: 30px; backdrop-filter: blur(4px);">
            <div id="companion-drag-handle" style="cursor: grab; display: flex; align-items: center; justify-content: center; width: 40px; height: 40px; background: white; border-radius: 50%; box-shadow: 0 2px 6px rgba(0,0,0,0.2);" title="드래그하여 이동">✥</div>
            <button id="companion-chat-open" style="pointer-events: auto; padding: 10px 20px; background: var(--forest, #4A6741); color: white; border: 2px solid white; border-radius: 24px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 12px rgba(0,0,0,0.3); font-size: 14px; transition: all 0.2s;">💬 대화하기</button>
        </div>
    </div>
'''

new_block = '''    <!-- 2. 캐릭터 캔버스 -->
    <button id="companion-chat-open" style="pointer-events: auto; position: fixed; right: 40px; bottom: 40px; padding: 12px 24px; background: var(--forest, #4A6741); color: white; border: 2px solid white; border-radius: 30px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 16px rgba(0,0,0,0.3); font-size: 15px; transition: all 0.2s; z-index: 10000;">💬 대화하기</button>
    <div id="companion-character-wrap" style="position: fixed; right: 180px; bottom: 20px; display: flex; flex-direction: column; justify-content: flex-end; align-items: flex-end; pointer-events: none; z-index:9999;">
        <canvas id="companion-canvas" style="pointer-events: auto; cursor: grab; filter: drop-shadow(0px 4px 6px rgba(0,0,0,0.2)); transition: filter 0.2s;"></canvas>
    </div>
'''

text = text.replace(target, new_block)
text = re.sub(r'companion-widget\.js\?v=\d+', 'companion-widget.js?v=25', text)

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/fragments/layout.html', 'w', encoding='utf-8') as f:
    f.write(text)