import sys

def fix_file(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find the string directly bypassing backslash issues
    old_code = r'responseText = responseText.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");'
    new_code = '''int startIndex = responseText.indexOf("{");
        int endIndex = responseText.lastIndexOf("}");
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            responseText = responseText.substring(startIndex, endIndex + 1);
        }'''
    
    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Fixed {filename}")
    else:
        print(f"Code not found in {filename}")

fix_file("src/main/java/net/datasa/tanoshimi/controller/PlannerController.java")
