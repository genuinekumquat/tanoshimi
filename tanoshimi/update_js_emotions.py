import re

js_path_src = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js'
js_path_build = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/static/js/companion-widget.js'

with open(js_path_src, 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Update react function to include a 3-second timeout
react_old = '''    function react(emotion) {
        if (!live2dModel) return;
        const motionByEmotion = { greet: 'tap_body', thinking: 'flick_head', happy: 'tap_body', angry: 'tap_body', sad: 'flick_head', shy: 'tap_body', normal: 'tap_body' };
        try { live2dModel.motion(motionByEmotion[emotion] || 'tap_body'); } catch (e) { }
        try {
            if (emotion === 'sad') window.currentVivianEmotion = 'Param144';
            else if (emotion === 'shy' || emotion === 'happy') window.currentVivianEmotion = 'Param149';
            else if (emotion === 'angry') window.currentVivianEmotion = 'Param150';
            else if (emotion === 'thinking') window.currentVivianEmotion = 'Param132';
            else window.currentVivianEmotion = null;
        } catch (e) { console.warn('표정 변경 실패', e); }
    }'''
# wait, the file has it wrapped in some spacing. Let's just string match it safely.
start = text.find('function react(emotion) {')
end = text.find('}', text.find('catch (e) { console.warn', start)) + 1

if start != -1 and end != -1:
    react_new = '''    function react(emotion) {
        if (!live2dModel) return;
        const motionByEmotion = { greet: 'tap_body', thinking: 'flick_head', happy: 'tap_body', angry: 'tap_body', sad: 'flick_head', shy: 'tap_body', normal: 'tap_body' };
        try { live2dModel.motion(motionByEmotion[emotion] || 'tap_body'); } catch (e) { }
        try {
            if (emotion === 'sad') window.currentVivianEmotion = 'Param144';
            else if (emotion === 'shy' || emotion === 'happy') window.currentVivianEmotion = 'Param149';
            else if (emotion === 'angry') window.currentVivianEmotion = 'Param150';
            else if (emotion === 'thinking') window.currentVivianEmotion = 'Param132';
            else window.currentVivianEmotion = null;

            // 3초 뒤에 원래 표정으로 리셋
            if (window.vivianEmotionTimeout) clearTimeout(window.vivianEmotionTimeout);
            if (window.currentVivianEmotion !== null) {
                window.vivianEmotionTimeout = setTimeout(() => {
                    window.currentVivianEmotion = null;
                }, 3000);
            }
        } catch (e) { console.warn('표정 변경 실패', e); }
    }'''
    text = text[:start] + react_new + text[end:]

# 2. Update makeDraggable pointerdown to use react('shy')
pointerdown_old = '''handle.classList.add("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 10px 15px rgba(0,0,0,0.4))";
                        window.currentVivianEmotion = 'Param149';'''
pointerdown_new = '''handle.classList.add("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 10px 15px rgba(0,0,0,0.4))";
                        react('shy');'''
text = text.replace(pointerdown_old, pointerdown_new)

# 3. Update makeDraggable pointerup to use react('normal')
pointerup_old = '''handle.classList.remove("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 4px 6px rgba(0,0,0,0.2))";
                        window.currentVivianEmotion = null;'''
pointerup_new = '''handle.classList.remove("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 4px 6px rgba(0,0,0,0.2))";
                        react('normal');'''
text = text.replace(pointerup_old, pointerup_new)

# 4. updateScale needs to update the bubble's transform variable if it isn't updating automatically by inheriting
scale_old = "canvas.style.setProperty('--vivian-scale', uiScale);"
scale_new = "canvas.style.setProperty('--vivian-scale', uiScale);\n                const bubble = document.getElementById('companion-speech-bubble');\n                if (bubble) bubble.style.setProperty('--vivian-scale', uiScale);"
text = text.replace(scale_old, scale_new)

# 5. togglePanel also updates vivian-scale
toggle_old = "canvas.style.setProperty('--vivian-scale', sc);"
toggle_new = "canvas.style.setProperty('--vivian-scale', sc);\n        const bubble = document.getElementById('companion-speech-bubble');\n        if (bubble) bubble.style.setProperty('--vivian-scale', sc);"
text = text.replace(toggle_old, toggle_new)

with open(js_path_src, 'w', encoding='utf-8') as f:
    f.write(text)
with open(js_path_build, 'w', encoding='utf-8') as f:
    f.write(text)