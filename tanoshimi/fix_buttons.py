import json

def patch_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        text = f.read()

    text = text.replace('color:var(--ink-soft); background:none; border:none; cursor:pointer;">신고', 'font-weight:700; color:var(--danger, red); background:var(--parchment, #f4f5f7); padding:4px 10px; border-radius:6px; border:1px solid #ffcccc; cursor:pointer;">?? 신고')
    
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)

patch_file("src/main/resources/templates/board/detail.html")
patch_file("src/main/resources/templates/party/detail.html")
print("Done")
