import re

path = 'src/main/resources/templates/mypage/index.html'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

target = '<span style="font-size:12px;color:var(--ink-soft);font-family:\'Gowun Dodum\',sans-serif;">님</span>'
manner_ui = '''<span style="font-size:12px;color:var(--ink-soft);font-family:\'Gowun Dodum\',sans-serif;">님</span>
          <span style="margin-left:auto; display:inline-flex; align-items:center; gap:6px; font-size:13px; font-family:'Gowun Dodum',sans-serif;">
            <span style="color:#ff8a00; font-weight:bold;" th:text="${me.mannerScore + '°C'}">36.5°C</span>
            <div style="width:60px; height:6px; background:#eee; border-radius:3px; overflow:hidden;">
               <div th:style="'height:100%; background:#ff8a00; width:' + (${me.mannerScore}/100*100) + '%;'"></div>
            </div>
          </span>'''

text = text.replace(target, manner_ui)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
