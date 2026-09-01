import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix the async syntax error
text = text.replace('async document.head.insertAdjacentHTML', 'document.head.insertAdjacentHTML')
text = text.replace('\);\n    function initLive2D() {', '\);\n    async function initLive2D() {')

# Check if pointerdown snippet is there
if 'handle.classList.add("dangle-animate")' not in text:
    target = "handle.style.cursor = 'grabbing';"
    insertion = '''handle.style.cursor = 'grabbing';
                    if(elId === "companion-character-wrap") {
                        handle.classList.add("dangle-animate");
                        handle.style.filter = "drop-shadow(0px 10px 15px rgba(0,0,0,0.4))";
                        if(window.currentVivianModel && window.currentVivianModel.internalModel.coreModel) window.currentVivianModel.internalModel.coreModel.addParameterValueById("Param132", 1);
                    }'''
    text = text.replace(target, insertion)

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)