import sys

def fix_unreachable(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    # Find the line immediately following the catch block closing brace which contains the unreachable return
    with open(filename, 'w', encoding='utf-8') as f:
        skip_next = False
        for line in lines:
            if 'return "어라, 지금 통신이 잘 안 되네...' in line:
                continue
            if '📡' in line:
                # If there's an emoji encoding issue, we skip lines that contain parts of the string
                if '어라, 지금 통신이 잘 안 되네' in line:
                    continue
            f.write(line)
            
    print(f"Fixed {filename}")

fix_unreachable("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")
