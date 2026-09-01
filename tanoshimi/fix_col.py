import sys

def fix_colors(filename_html, filename_js):
    with open(filename_html, 'r', encoding='utf-8') as f:
        html = f.read()
        
    old_input = '<input type="color" id="custom-slot-color" title="색상 커스텀" value="#ffa500" style="width:30px; height:30px; padding:0; border:none; cursor:pointer;" />'
    new_input = """<input type="color" id="custom-slot-color" title="배경 색상 커스텀" value="#ffa500" style="width:30px; height:30px; padding:0; border:none; cursor:pointer;" />
          <input type="color" id="custom-text-color" title="기존 계획표 글자색상" value="#4b6b4a" style="width:30px; height:30px; padding:0; border:none; cursor:pointer; margin-right:8px;" />"""
    
    if old_input in html:
        html = html.replace(old_input, new_input)
    
    # Let's map --custom-text-color to .block .t
    old_css = ".block.blank .t{ color:var(--ink); }"
    new_css = ".block.blank .t{ color:var(--custom-text-color, var(--ink)); }"
    if old_css in html:
        html = html.replace(old_css, new_css)
        
    with open(filename_html, 'w', encoding='utf-8') as f:
        f.write(html)
        
    with open(filename_js, 'r', encoding='utf-8') as f:
        js = f.read()
        
    # We add custom-text-color event listener
    js_listen = """    const ctc = document.getElementById('custom-text-color');
    if (ctc) {
        ctc.value = localStorage.getItem('custom-text-color') || '#4b6b4a';
        document.documentElement.style.setProperty('--custom-text-color', ctc.value);
        ctc.addEventListener('input', e => {
            localStorage.setItem('custom-text-color', e.target.value);
            document.documentElement.style.setProperty('--custom-text-color', e.target.value);
            // Also override the inline style of all blocks if they were using the global one
            document.querySelectorAll('.block .t').forEach(el => {
                if (!el.style.color || el.style.color === 'inherited' || el.style.color === '') {
                    el.style.color = e.target.value;
                }
            });
        });
    }"""
    
    if 'document.getElementById(\'custom-text-color\')' not in js:
        js = js.replace('buildGrid();', js_listen + '\n\n    buildGrid();')
        
    # In planner.js render, we used `color: ${item.color || ''};`.
    # Let's change it to `color: ${item.color || 'var(--custom-text-color, #4b6b4a)'};`
    js = js.replace('color: ${item.color || \'\'};', 'color: ${item.color || \'var(--custom-text-color, #4b6b4a)\'};')
    
    with open(filename_js, 'w', encoding='utf-8') as f:
        f.write(js)

fix_colors("src/main/resources/templates/planner/index.html", "src/main/resources/static/js/planner.js")
