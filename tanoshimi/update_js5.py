import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'r', encoding='utf-8') as f:
    text = f.read()

print("Original text length:", len(text))

correct_makeDraggable = """            function makeDraggable(elId, handleId, storageKey) {
                const el = document.getElementById(elId);
                const handle = document.getElementById(handleId);
                if (!el || !handle) return;
                let isDown = false, startX, startY, startLeft, startTop;
                
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
                };

                handle.addEventListener('pointerdown', (e) => {
                    if (e.target.tagName === 'BUTTON' || e.target.tagName === 'INPUT') return;
                    if (isTransparent(e)) {
                        handle.style.display = 'none';
                        const below = document.elementFromPoint(e.clientX, e.clientY);
                        handle.style.display = 'block';
                        if (below) below.click();
                        return;
                    }
                    isDown = true;
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
                        if (isTransparent(e)) return;
                        react('angry');
                    });
                }
            }"""

# Actually, the file right now ALREADY has the broken version.
# Let's just find the broken part and replace it.
broken_part = '''                    if (isDown) {
                        isDown = false;
                }
            }'''
fix = '''                    if (isDown) {
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
                        if (isTransparent(e)) return;
                        react('angry');
                    });
                }
            }'''

text = text.replace(broken_part, fix)

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)
with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)