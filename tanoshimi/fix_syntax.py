import sys

def fix_syntax(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # The broken string is `replace(""", "'")`
    content = content.replace('replace(""", "\'")', 'replace("\\"", "\'")')
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_syntax("src/main/java/net/datasa/tanoshimi/util/RealGeminiClient.java")
