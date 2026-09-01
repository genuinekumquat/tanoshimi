import sys
import re

def rewrite(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    new_toggle = """    window.toggleCharacterVisibility = function(forceHide) {
        const isCurrentlyHidden = canvas.style.opacity === '0';
        const willHide = forceHide !== undefined ? forceHide : !isCurrentlyHidden;
        
        canvas.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
        canvas.style.opacity = willHide ? '0' : '1';
        canvas.style.pointerEvents = willHide ? 'none' : 'auto';
        
        if (toggleBtn) {
            toggleBtn.textContent = willHide ? '🙈' : '👀';
            toggleBtn.title = willHide ? '캐릭터 보이기' : '캐릭터 숨기기';
        }
        const ghostBtn = document.getElementById('companion-ghost-btn');
        if (ghostBtn) {
            ghostBtn.innerHTML = willHide ? '👻 표시하기' : '👻 숨기기';
            ghostBtn.style.background = willHide ? '#a5d6a7' : '#ffeb3b';
        }
        
        localStorage.setItem('companion_hidden_state', willHide ? 'true' : 'false');
    };"""

    # Using regex to replace the old function completely
    pattern = re.compile(r'window\.toggleCharacterVisibility = function\(\) \{.+?^\s+?\};', re.DOTALL | re.MULTILINE)
    content = pattern.sub(new_toggle, content)

    # find `toggleBtn?.addEventListener('click', (e) => {` block to insert the init logic right before it
    init_logic = """
    const savedHidden = localStorage.getItem('companion_hidden_state');
    if (savedHidden === 'true') {
        window.toggleCharacterVisibility(true);
    }
"""
    if "('companion_hidden_state')" not in content:
        content = content.replace("toggleBtn?.addEventListener('click', (e) => {", init_logic + "\n    toggleBtn?.addEventListener('click', (e) => {")

    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)

rewrite("src/main/resources/static/js/companion-widget.js")
