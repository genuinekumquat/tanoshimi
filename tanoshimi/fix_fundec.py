import sys

def fix_geminichatclient(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # Change `"function_declarations"` to `"functionDeclarations"`
    if '"function_declarations"' in content:
        content = content.replace('"function_declarations"', '"functionDeclarations"')
        
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_geminichatclient("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")
