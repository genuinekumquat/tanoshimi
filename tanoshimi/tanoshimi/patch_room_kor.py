import re

try:
    with open('src/main/resources/templates/party/room.html', 'r', encoding='utf-8') as f:
        html = f.read()

    replacements = {
        # Board
        "지??/span>": "지역</span>",
        "?청??리스??": "신청자 리스트",
        "?청??목록 (방장?": "신청자 목록 (방장용)",
        "참? ?청??/h2>": "참여 신청자</h2>",
        "???가??어??": "함께 가고 싶어요!",
        "?직 공유???진???습?다.": "아직 공유된 사진이 없습니다.",
        "?측 : 채팅 ??표": "우측 : 채팅 및 투표",
        "?정 ?스 ?표 (?약 ???계?": "일정 취소 투표 (예약 취소용)",
        "???정 ?스 ?표": "현재 일정 취소 투표",
        "모두가 찬성?여 ?행 ?키지 ?약???정?었?니??": "모두가 찬성하여 여행 패키지 예약 취소가 결정되었습니다.",
        "????/button>": "나가기</button>",
        "메시지??력?세??..": "메시지를 입력하세요...",
        "? 오픈??채팅방이 ?습?다.": "오픈된 채팅방이 있습니다.",
        "?????아주세??..": "댓글을 달아주세요...",
        "?목???력?세??": "제목을 입력하세요",
        "?용?나 ?감???어보세??": "내용이나 소감을 적어보세요",
        "채팅 ?버 ?속??기다?주?요.": "채팅 서버 접속을 기다려주세요.",
        "?말 ???티?서 ???겠?니?": "정말 이 파티에서 나가시겠습니까?",
        "?진???첨??주?요.": "사진을 꼭 첨부해주세요.",
        "????불러?는 ?..": "댓글을 불러오는 중...",
        "??????성?보?요!": "첫 댓글을 작성해보세요!",
        "??????성 ?.. (?료 ???록 버튼 ?릭)": "대댓글 작성 중... (완료 시 등록 버튼 클릭)",
        "????불러?는???패?습?다.": "댓글을 불러오는데 실패했습니다.",
        "?류가 발생?습?다.": "오류가 발생했습니다.",
        "취소</button>": "취소</button>",
        "?리?/button>": "올리기</button>",
        "추억 ?진": "추억 사진"
    }

    for k, v in replacements.items():
        html = html.replace(k, v)
        
    # Replace all broken tags for titles etc
    html = re.sub(r'\?[^<>\n]*\?[\w가-힣]+', '...', html)

    with open('src/main/resources/templates/party/room.html', 'w', encoding='utf-8') as f:
        f.write(html)
        
    print("room.html patched")
except Exception as e:
    import traceback
    traceback.print_exc()

