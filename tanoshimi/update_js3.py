import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Add preserveDrawingBuffer
app_target = "width: APP_WIDTH, height: APP_HEIGHT,"
app_repl = "width: APP_WIDTH, height: APP_HEIGHT, preserveDrawingBuffer: true,"
text = text.replace(app_target, app_repl)

# 2. Add pixel check helper function inside makeDraggable
helper_target = "let isDown = false, startX, startY, startLeft, startTop;"
helper_repl = '''let isDown = false, startX, startY, startLeft, startTop;
                const isTransparent = (e) => {
                    if (elId !== "companion-character-wrap") return false;
                    const gl = handle.getContext('webgl2') || handle.getContext('webgl');
                    if (!gl) return false;
                    const pixels = new Uint8Array(4);
                    const rect = handle.getBoundingClientRect();
                    const px = (e.clientX - rect.left) * (handle.width / rect.width);
                    const py = (e.clientY - rect.top) * (handle.height / rect.height);
                    gl.readPixels(px, handle.height - py, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
                    return pixels[3] === 0;
                };'''
text = text.replace(helper_target, helper_repl)

# 3. Use isTransparent in pointerdown
pd_target = '''handle.addEventListener('pointerdown', (e) => {
                    if (e.target.tagName === 'BUTTON' || e.target.tagName === 'INPUT') return;'''
pd_repl = '''handle.addEventListener('pointerdown', (e) => {
                    if (e.target.tagName === 'BUTTON' || e.target.tagName === 'INPUT') return;
                    if (isTransparent(e)) {
                        // pass through visually to not block drag, but actually we can just return
                        // To make it click through easily without complex event delegation,
                        // we can temporarily hide canvas and throw a click at the element below.
                        handle.style.display = 'none';
                        const below = document.elementFromPoint(e.clientX, e.clientY);
                        handle.style.display = 'block';
                        if (below) below.click();
                        return;
                    }'''
text = text.replace(pd_target, pd_repl)

# 4. Use isTransparent in dblclick
dbl_target = '''handle.addEventListener('dblclick', () => {
                        react('angry');
                    });'''
dbl_repl = '''handle.addEventListener('dblclick', (e) => {
                        if (isTransparent(e)) return;
                        react('angry');
                    });'''
text = text.replace(dbl_target, dbl_repl)


with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)
with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)