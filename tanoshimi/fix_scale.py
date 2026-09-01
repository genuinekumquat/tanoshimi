import sys
import re

def fix_scale(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Update togglePanel
    old_toggle = """    function togglePanel() {
        const opening = panel.style.display === 'none' || !panel.style.display;
        panel.style.display = opening ? 'flex' : 'none';
        canvas.style.transition = 'transform 0.3s ease';
        canvas.style.transformOrigin = 'bottom right';
        const sc = opening ? 0.8 : (parseFloat(localStorage.getItem('companion_scale') || 1.0));
        canvas.style.setProperty('--vivian-scale', sc);
        const bubble = document.getElementById('companion-speech-bubble');
        if (bubble) bubble.style.setProperty('--vivian-scale', sc);
        canvas.style.transform = 'scale(' + sc + ')';
        if (opening) {
            renderHistory();
            input.focus();
        }
    }"""
    
    new_toggle = """    function togglePanel() {
        const opening = panel.style.display === 'none' || !panel.style.display;
        panel.style.display = opening ? 'flex' : 'none';
        
        // We no longer force scale to 0.8 when opening. Just apply the saved scale.
        const sc = parseFloat(localStorage.getItem('companion_scale') || 1.0);
        canvas.style.setProperty('--vivian-scale', sc);
        const bubble = document.getElementById('companion-speech-bubble');
        if (bubble) bubble.style.setProperty('--vivian-scale', sc);
        canvas.style.transform = 'scale(' + sc + ')';
        
        if (opening) {
            renderHistory();
            input.focus();
        }
    }"""
    
    if old_toggle in content:
        content = content.replace(old_toggle, new_toggle)
        
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
        
    print(f"Fixed {filename}")

fix_scale("src/main/resources/static/js/companion-widget.js")
