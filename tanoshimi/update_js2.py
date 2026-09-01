import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix 1: The keyframes CSS to use --vivian-scale
new_keyframe = '''@keyframes dangle {
          0% { transform: scale(var(--vivian-scale, 1)) rotate(-5deg) translateY(0); }
          100% { transform: scale(var(--vivian-scale, 1)) rotate(5deg) translateY(-10px); }
      }'''
text = re.sub(r'@keyframes dangle \{.*?\n      \}', new_keyframe, text, flags=re.DOTALL)

# Fix 2: updateScale to set --vivian-scale
new_updateScale = '''function updateScale(uiScale) {
                canvas.style.setProperty('--vivian-scale', uiScale);
                canvas.style.transform = 'scale(' + uiScale + ')';
                canvas.style.transformOrigin = 'bottom right';
            }'''
text = re.sub(r'function updateScale\(uiScale\) \{.*?\}', new_updateScale, text, flags=re.DOTALL)

# Fix 3: togglePanel to set --vivian-scale
new_togglePanel = '''function togglePanel() {
        const opening = panel.style.display === 'none' || !panel.style.display;
        panel.style.display = opening ? 'flex' : 'none';
        canvas.style.transition = 'transform 0.3s ease';
        canvas.style.transformOrigin = 'bottom right';
        const sc = opening ? 0.8 : (parseFloat(localStorage.getItem('companion_scale') || 1.0));
        canvas.style.setProperty('--vivian-scale', sc);
        canvas.style.transform = 'scale(' + sc + ')';
        if (opening) {
            renderHistory();
            input.focus();
        }
    }'''
text = re.sub(r'function togglePanel\(\) \{.*?\}', new_togglePanel, text, flags=re.DOTALL)

# Fix 4: pointerdown and pointerup for shy expression instead of just parameter setting
# We replace the entire block inside pointerdown where elId == companion-character-wrap
target_down = '''if(elId === "companion-character-wrap") {
                        handle.classList.add("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 10px 15px rgba(0,0,0,0.4))";
                        if(window.currentVivianModel && window.currentVivianModel.internalModel.coreModel) window.currentVivianModel.internalModel.coreModel.addParameterValueById("Param132", 1);
                    }'''
new_down = '''if(elId === "companion-character-wrap") {
                        handle.classList.add("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 10px 15px rgba(0,0,0,0.4))";
                        window.currentVivianEmotion = 'Param149'; // Shy
                    }'''
text = text.replace(target_down, new_down)

target_up = '''if(el.id === "companion-character-wrap") {
                        handle.classList.remove("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 4px 6px rgba(0,0,0,0.2))";
                    }'''
new_up = '''if(el.id === "companion-character-wrap") {
                        handle.classList.remove("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 4px 6px rgba(0,0,0,0.2))";
                        window.currentVivianEmotion = null; // Revert
                    }'''
text = text.replace(target_up, new_up)

# Fix 5: Double click to angry
# We can inject this simply at the end of makeDraggable
dblclick_inject = '''
                if (elId === "companion-character-wrap") {
                    handle.addEventListener('dblclick', () => {
                        react('angry');
                    });
                }
'''
text = text.replace('} catch(e) {}', dblclick_inject + '\n                } catch(e) {}')

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)
with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)