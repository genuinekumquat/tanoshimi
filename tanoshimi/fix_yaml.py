import sys

def fix_yaml(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()
        
    old_model = 'model: gemini-2.5-flash'
    new_model = 'model: gemini-1.5-flash-8b' # 8b has very high rate limits
    
    if old_model in content:
        content = content.replace(old_model, "model: gemini-1.5-flash") # normal 1.5 flash
        
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_yaml("src/main/resources/application.yml")
