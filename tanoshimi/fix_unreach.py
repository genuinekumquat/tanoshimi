import sys

def fix_unreachable(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    target = 'return "어라, 지금 통신이 잘 안 되네... 잠시 후 다시 말 걸어줄래? 📡";'
    # Actually wait, maybe it's encoding. I'll just regex
    import re
    content = re.sub(r'return\s+"어라, 지금 통신이 잘 안 되네\.\.\. 잠시 후 다시 말 걸어줄래\? \ud83d\udce1";', '', content)
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_unreachable("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")
