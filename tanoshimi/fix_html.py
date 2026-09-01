import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/fragments/layout.html', 'r', encoding='utf-8') as f:
    text = f.read()

text = re.sub(
    r'<!-- 2\. 캐릭터 캔버스 -->.*?</div>\s*</div>',
    '''<!-- 2. 캐릭터 캔버스 -->
    <button id="companion-chat-open" style="pointer-events: auto; position: fixed; right: 40px; bottom: 40px; padding: 12px 24px; background: #4A6741; color: white; border: 2px solid white; border-radius: 30px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 16px rgba(0,0,0,0.3); font-size: 15px; transition: all 0.2s; z-index: 10000;">💬 대화하기</button>
    <div id="companion-character-wrap" style="position: fixed; right: 180px; bottom: 20px; display: flex; flex-direction: column; justify-content: flex-end; align-items: flex-end; pointer-events: none; z-index:9999;">
        <canvas id="companion-canvas" style="pointer-events: auto; cursor: grab; filter: drop-shadow(0px 4px 6px rgba(0,0,0,0.2)); transition: filter 0.2s;"></canvas>
    </div>''',
    text,
    flags=re.DOTALL
)

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/fragments/layout.html', 'w', encoding='utf-8') as f:
    f.write(text)