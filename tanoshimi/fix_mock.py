import sys

def fix_mock(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # Change havingValue = "gemini" to havingValue = "mock"
    old_ann = '@ConditionalOnProperty(name = "app.companion.provider", havingValue = "gemini", matchIfMissing = true)'
    new_ann = '@ConditionalOnProperty(name = "app.companion.provider", havingValue = "mock", matchIfMissing = true)'
    
    if old_ann in content:
        content = content.replace(old_ann, new_ann)
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Fixed {filename}")
    else:
        print("Could not find exact annotation.")

fix_mock("src/main/java/net/datasa/tanoshimi/util/MockGeminiClient.java")
