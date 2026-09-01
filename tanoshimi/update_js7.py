import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'r', encoding='utf-8') as f:
    text = f.read()

full_makeDraggable = '''            window.isDraggingVivian = false;

            function makeDraggable(elId, handleId, storageKey) {
                const el = document.getElementById(elId);
                const handle = document.getElementById(handleId);
                if (!el || !handle) return;
                let isDown = false, startX, startY, startLeft, startTop;

                // For character, we want exact pixel hover detection
                if (elId === "companion-character-wrap") {
                    handle.style.pointerEvents = 'none'; // Default to transparent
                    window.addEventListener('pointermove', (e) => {
                        if (window.isDraggingVivian) {
                            handle.style.pointerEvents = 'auto';
                            return;
                        }

                        // Check if within bounds
                        const rect = handle.getBoundingClientRect();
                        if (e.clientX < rect.left || e.clientX > rect.right || e.clientY < rect.top || e.clientY > rect.bottom) {
                            handle.style.pointerEvents = 'none';
                            return;
                        }

                        const gl = handle.getContext('webgl2') || handle.getContext('webgl');
                        if (!gl) return;
                        
                        try {
                            const pixels = new Uint8Array(4);
                            const px = (e.clientX - rect.left) * (handle.width / rect.width);
                            const py = (e.clientY - rect.top) * (handle.height / rect.height);
                            gl.readPixels(px, handle.height - py, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
                            
                            handle.style.pointerEvents = (pixels[3] === 0) ? 'none' : 'auto';
                        } catch(err) {}
                    }, { passive: true });
                }

                handle.addEventListener('pointerdown', (e) => {
                    if (e.target.tagName === 'BUTTON' || e.target.tagName === 'INPUT') return;
                    isDown = true;
                    if (elId === "companion-character-wrap") window.isDraggingVivian = true;
                    
                    startX = e.clientX;
                    startY = e.clientY;
                    const rect = el.getBoundingClientRect();
                    startLeft = rect.left;
                    startTop = rect.top;
                    el.style.right = 'auto';
                    el.style.bottom = 'auto';
                    el.style.left = startLeft + 'px';
                    el.style.top = startTop + 'px';
                    handle.style.cursor = 'grabbing';
                    
                    if(elId === "companion-character-wrap") {
                        handle.classList.add("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 10px 15px rgba(0,0,0,0.4))";
                        window.currentVivianEmotion = 'Param149';
                    }
                });
                
                window.addEventListener('pointermove', (e) => {
                    if (!isDown) return;
                    e.preventDefault();
                    el.style.left = (startLeft + e.clientX - startX) + 'px';
                    el.style.top = (startTop + e.clientY - startY) + 'px';
                }, { passive: false });
                
                window.addEventListener('pointerup', () => {
                    if(el.id === "companion-character-wrap") {
                        handle.classList.remove("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 4px 6px rgba(0,0,0,0.2))";
                        window.currentVivianEmotion = null;
                        if (isDown) window.isDraggingVivian = false;
                    }

                    if (isDown) {
                        isDown = false;
                        handle.style.cursor = 'grab';
                        try {
                            const rect = el.getBoundingClientRect();
                            const pos = { left: rect.left, top: rect.top };
                            localStorage.setItem(storageKey, JSON.stringify(pos));
                        } catch (e) {}
                    }
                });

                if (elId === "companion-character-wrap") {
                    handle.addEventListener('dblclick', (e) => {
                        react('angry');
                    });
                }
            }'''

# Find the start index
start = text.find('function makeDraggable(elId, handleId, storageKey)')
end = text.find("makeDraggable('companion-character-wrap'", start)

if start != -1 and end != -1:
    text = text[:start] + full_makeDraggable + "\n\n            " + text[end:]
else:
    print("COULD NOT FIND!")

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)
with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)