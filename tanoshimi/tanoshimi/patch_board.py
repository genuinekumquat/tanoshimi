import re

# 1. Fix template parsing error in party/board.html
try:
    with open('src/main/resources/templates/party/board.html', 'r', encoding='utf-8') as f:
        board_html = f.read()

    # The broken expression
    bad_expr = "th:text=\"${party.departureDate.isEqual(today)} ? 'D-DAY' : (${party.departureDate.isAfter(today)} ? 'D-' + T(java.time.temporal.ChronoUnit).DAYS.between(today, party.departureDate) : '종료')\""
    
    # The fixed expression
    good_expr = "th:text=\"${party.departureDate.isEqual(today) ? 'D-DAY' : (party.departureDate.isAfter(today) ? 'D-' + T(java.time.temporal.ChronoUnit).DAYS.between(today, party.departureDate) : '종료')}\""

    board_html = board_html.replace(bad_expr, good_expr)

    with open('src/main/resources/templates/party/board.html', 'w', encoding='utf-8') as f:
        f.write(board_html)
        
    print("party/board.html updated")
except Exception as e:
    import traceback
    traceback.print_exc()

# 2. Add click-outside to close modals in party/room.html
try:
    with open('src/main/resources/templates/party/room.html', 'r', encoding='utf-8') as f:
        room_html = f.read()

    script_inject = """
    document.getElementById('v-comment-submit')?.addEventListener('click', async () => {
"""
    new_script = """
    document.querySelectorAll('.modal-overlay').forEach(modal => {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.style.display = 'none';
            }
        });
    });

    document.getElementById('v-comment-submit')?.addEventListener('click', async () => {
"""
    
    room_html = room_html.replace(script_inject, new_script)

    with open('src/main/resources/templates/party/room.html', 'w', encoding='utf-8') as f:
        f.write(room_html)
    print("party/room.html updated")
except Exception as e:
    import traceback
    traceback.print_exc()
