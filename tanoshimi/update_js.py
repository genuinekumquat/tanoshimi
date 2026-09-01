import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Update `appendBubble`
# Let's find function appendBubble(who, text) { ... }
# And inject the display logic for speech bubble and the checkbox filter.
append_func = '''    function appendBubble(who, text) {
        if (who === 'bot') {
            const popup = document.getElementById('companion-speech-bubble');
            if (popup) {
                popup.innerText = text;
                popup.style.display = 'block';
                if (window.companionSpeechTimeout) clearTimeout(window.companionSpeechTimeout);
                window.companionSpeechTimeout = setTimeout(() => {
                    popup.style.display = 'none';
                }, 12000);
            }
            const showCheck = document.getElementById('companion-show-bot-chat');
            if (showCheck && !showCheck.checked) {
                return;
            }
        }
'''
text = re.sub(r'function appendBubble\(who, text\) \{', append_func, text)

# 2. Add event listener for checkbox in initLive2D
# We'll just put it right after toggleBtn event... wait, we can just put it at the very bottom of initLive2D, before catch block.
# Actually let's just find "const sendBtn = document.getElementById" and hook it globally. Wait, checkbox is in the panel, so it's globally available.
init_check_code = '''
    const showBotChatCheckbox = document.getElementById('companion-show-bot-chat');
    if (showBotChatCheckbox) {
        showBotChatCheckbox.addEventListener('change', () => {
            const logPanel = document.getElementById('companion-chat-log');
            if(logPanel) logPanel.innerHTML = '';
            renderHistory();
        });
    }

    if (!canvas) return;
'''
text = text.replace('if (!canvas) return;', init_check_code)

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)