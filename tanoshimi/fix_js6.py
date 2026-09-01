import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix the mess
pattern = re.compile(r'let live2dModel = null;.*?async function initLive2D', re.DOTALL)

good_inject = '''let live2dModel = null;

    const styleEl = document.createElement("style");
    styleEl.innerHTML = 
      @keyframes dangle {
          0% { transform: rotate(-5deg) translateY(0); }
          100% { transform: rotate(5deg) translateY(-10px); }
      }
      .dangle-animate {
          animation: dangle 0.4s infinite alternate ease-in-out;
      }
    ;
    document.head.appendChild(styleEl);

    async function initLive2D'''

text = pattern.sub(good_inject, text)

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)
with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/build/resources/main/static/js/companion-widget.js', 'w', encoding='utf-8') as f:
    f.write(text)