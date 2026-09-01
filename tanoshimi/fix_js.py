import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'r', encoding='utf-8') as f:
    text = f.read()

styles = '''
<style>
@keyframes dangle {
    0% { transform: rotate(-5deg) translateY(0); }
    100% { transform: rotate(5deg) translateY(-10px); }
}
.dangle-animate {
    animation: dangle 0.4s infinite alternate ease-in-out;
}
</style>
'''
if '@keyframes dangle' not in text:
    text = text.replace('function initLive2D() {', 'document.head.insertAdjacentHTML("beforeend", ' + styles + ');\n    function initLive2D() {')

text = text.replace("'companion-character-wrap', 'companion-drag-handle', 'companion_pos_char'",
                    "'companion-character-wrap', 'companion-canvas', 'companion_pos_char'")

# Safely inject into pointerdown
insert_down = '''
                    if(elId === "companion-character-wrap") {
                        handle.classList.add("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 10px 15px rgba(0,0,0,0.4))";
                    }
'''
if 'dangle-animate' not in text:
    text = text.replace("handle.style.cursor = 'grabbing';", "handle.style.cursor = 'grabbing';" + insert_down)

# Safely inject into pointerup
insert_up = '''
                    if(el.id === "companion-character-wrap") {
                        handle.classList.remove("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 4px 6px rgba(0,0,0,0.2))";
                    }
'''
text = text.replace("window.addEventListener('pointerup', () => {", "window.addEventListener('pointerup', () => {" + insert_up)


with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)