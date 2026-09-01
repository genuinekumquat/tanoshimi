import sys

def fix_google(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    content = content.replace('"google_search"', '"googleSearch"')
        
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_google("src/main/java/net/datasa/tanoshimi/util/RealGeminiClient.java")
