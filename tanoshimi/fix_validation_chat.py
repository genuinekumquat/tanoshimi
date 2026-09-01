import sys

def fix_planner_js(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # Need to find the ai-validate logic
    old_code = """        try {
            if (vBubble) {
                if (window.companionTimeout) clearTimeout(window.companionTimeout);
                vBubble.innerText = `${mode} 추천 적용중...`;
                vBubble.style.display = 'block';
            }
            const res = await window.api.post(`/api/planner/${SCHEDULE_ID}/ai-validate?mode=${encodeURIComponent(mode)}`, {});
            if (res.success) {
                if (vBubble) {
                    vBubble.innerText = res.data.briefing;
                    if (window.companionTimeout) clearTimeout(window.companionTimeout);
                    window.companionTimeout = setTimeout(() => { vBubble.style.display = 'none'; }, 8000);
                }
                await reload();
            } else {
                if (vBubble) {
                    vBubble.innerText = '검증 실패: ' + res.message;
                    if (window.companionTimeout) clearTimeout(window.companionTimeout);
                    window.companionTimeout = setTimeout(() => { vBubble.style.display = 'none'; }, 5000);
                }
            }"""
            
    new_code = """        try {
            if (vBubble) {
                if (window.companionTimeout) clearTimeout(window.companionTimeout);
                vBubble.innerText = `${mode} 추천 적용중...`;
                vBubble.style.display = 'block';
            }
            const res = await window.api.post(`/api/planner/${SCHEDULE_ID}/ai-validate?mode=${encodeURIComponent(mode)}`, {});
            if (res.success) {
                if (vBubble) {
                    vBubble.innerText = res.data.briefing;
                    if (window.companionTimeout) clearTimeout(window.companionTimeout);
                    window.companionTimeout = setTimeout(() => { vBubble.style.display = 'none'; }, 8000);
                }
                
                // Also add to chat window if bubble func is accessible. 
                // Since this is inside (function () { }), bubble() is available.
                if (typeof bubble === 'function') {
                    bubble(res.data.briefing, 'bot');
                }
                
                await reload();
            } else {
                if (vBubble) {
                    vBubble.innerText = '검증 실패: ' + res.message;
                    if (window.companionTimeout) clearTimeout(window.companionTimeout);
                    window.companionTimeout = setTimeout(() => { vBubble.style.display = 'none'; }, 5000);
                }
                if (typeof bubble === 'function') {
                    bubble('검증 실패: ' + res.message, 'bot');
                }
            }"""
            
    content = content.replace(old_code, new_code)
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_planner_js("src/main/resources/static/js/planner.js")
