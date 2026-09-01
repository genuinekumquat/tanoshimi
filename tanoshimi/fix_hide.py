import sys

def fix_hide_state(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Update toggleCharacterVisibility to save state
    old_toggle = """    window.toggleCharacterVisibility = function() {
        const isHidden = canvas.style.opacity === '0';
        canvas.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
        canvas.style.opacity = isHidden ? '1' : '0';
        canvas.style.pointerEvents = isHidden ? 'auto' : 'none';
        
        if (toggleBtn) {
            toggleBtn.textContent = isHidden ? '👀' : '🙈';
            toggleBtn.title = isHidden ? '캐릭터 숨기기' : '캐릭터 보이기';
        }
        const ghostBtn = document.getElementById('companion-ghost-btn');
        if (ghostBtn) {
            ghostBtn.innerHTML = isHidden ? '👻 숨기기' : '👻 표시하기';
            ghostBtn.style.background = isHidden ? '#ffeb3b' : '#a5d6a7';
        }
    };"""
    
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
    
    if old_toggle in content:
        content = content.replace(old_toggle, new_toggle)
        
    # 2. Add an init block to apply the saved state on load
    # Find DOMContentLoaded wrapper logic.
    init_logic = """
    // Apply hidden state on init
    const savedHidden = localStorage.getItem('companion_hidden_state');
    if (savedHidden === 'true') {
        window.toggleCharacterVisibility(true);
    }
"""

    if 'companion_hidden_state' not in content:
        # insert at the end of DOMContentLoaded callback
        # Look for the last clearBtn event listener area
        import re
        content = re.sub(r'(sendBtn\?\.addEventListener\(\'click\', async \(\) => \{.+?\}\);)', r'\1' + '\n' + init_logic, content, count=1, flags=re.DOTALL)
        
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_hide_state("src/main/resources/static/js/companion-widget.js")
