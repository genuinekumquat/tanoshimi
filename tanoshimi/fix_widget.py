import sys

def fix_widget(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # The string to replace is "오빠~ 나 비비안이야! 여행 계획 세우는 거 도와줄게, 편하게 물어봐! 🧳"
    if "오빠" in content:
        content = content.replace("오빠~ 나 비비안이야! 여행 계획 세우는 거 도와줄게, 편하게 물어봐! 🧳", "(당신을 보며 반색하며) 오셨군요. 여행 준비는 제가 도와드릴 테니 신경 쓰지 말고 편하게 말씀하세요.")
        
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_widget("src/main/resources/static/js/companion-widget.js")
