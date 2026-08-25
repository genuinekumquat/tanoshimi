import os

def check_and_replace(file_path, old_str, new_str):
    with open(file_path, 'r', encoding='utf-8') as f:
        text = f.read()
    if 'data-report-type' not in old_str and 'data-report-type' in text and old_str not in text:
        pass # might be already patched
    
    text = text.replace(old_str, new_str)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(text)

# 1. board/list.html
# grid item target: <span class="likes" th:text="'❤️ ' + ${post.likeCount}">❤️ 0</span>
# feed item target: <span th:text="'@' + ${post.user.name}">@작성자</span>
b_list = 'src/main/resources/templates/board/list.html'

grid_old = '<span class="likes" th:text="\'❤️ \' + ${post.likeCount}">❤️ 0</span>'
grid_new = grid_old + '''
      <span class="report-btn" style="position:absolute; bottom:10px; right:10px; z-index:10;" sec:authorize="isAuthenticated()">
         <button type="button" th:unless="${post.user.id == #authentication.principal.id}" th:attr="data-report-type=\'post\',data-report-id=${post.id},data-report-label=${post.title}" style="font-size:11px; padding:3px 6px; background:rgba(255,255,255,0.9); border:1px solid #ffcccc; color:red; border-radius:4px; font-weight:bold; cursor:pointer;">🚨 신고</button>
      </span>'''
check_and_replace(b_list, grid_old, grid_new)

list_old = '<span th:text="\'@\' + ${post.user.name}">@작성자</span>'
list_new = list_old + '''
          <span class="report-btn" style="margin-left:auto;" sec:authorize="isAuthenticated()">
              <button type="button" th:unless="${post.user.id == #authentication.principal.id}" th:attr="data-report-type=\'post\',data-report-id=${post.id},data-report-label=${post.title}" style="font-size:11px; padding:3px 6px; background:#fff; border:1px solid #ffcccc; color:red; border-radius:4px; font-weight:bold; cursor:pointer;">🚨 신고</button>
          </span>'''
check_and_replace(b_list, list_old, list_new)

# 2. party/board.html
# card item target: <div style="position:absolute; top:10px; right:10px;
p_board = 'src/main/resources/templates/party/board.html'
p_old_target = "th:text=\"${party.departureDate.isEqual(today) ? 'D-DAY' : (party.departureDate.isAfter(today) ? 'D-' + T(java.time.temporal.ChronoUnit).DAYS.between(today, party.departureDate) : '종료')}\">D-7</span>"

p_new_target = p_old_target + '''
            <!-- report button -->
            <span style="position:absolute; bottom:10px; right:10px; z-index:10;" sec:authorize="isAuthenticated()">
              <button type="button" th:unless="${party.author.id == #authentication.principal.id}" th:attr="data-report-type=\'party\',data-report-id=${party.id},data-report-label=${party.title}" style="font-size:11px; padding:3px 6px; background:rgba(255,255,255,0.9); border:1px solid #ffcccc; color:red; border-radius:4px; font-weight:bold; cursor:pointer;">🚨 신고</button>
            </span>'''
check_and_replace(p_board, p_old_target, p_new_target)

print("Patch applied")