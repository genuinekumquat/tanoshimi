import re

# 1. Update layout.html to add admin menu
try:
    with open('src/main/resources/templates/fragments/layout.html', 'r', encoding='utf-8') as f:
        layout = f.read()

    admin_li = '''<li sec:authorize="hasRole('ADMIN')"><a th:href="@{/admin}" style="display:block; padding:12px 16px; border-radius:8px; font-size:16px; font-weight:600; text-decoration:none; color:var(--danger); transition:0.2s;" onmouseover="this.style.background='#fff4f4'" onmouseout="this.style.background='none'">👑 관리자 메뉴</a></li>\n            '''
    
    # insert before packages
    layout = layout.replace('<li><a th:href="@{/packages}"', admin_li + '<li><a th:href="@{/packages}"')

    # Also add it to profile dropdown for good measure
    admin_a = '''<a sec:authorize="hasRole('ADMIN')" th:href="@{/admin}" style="display:block; padding:12px 16px; font-size:14px; color:var(--danger); font-weight:700; text-decoration:none; transition:background 0.2s;" onmouseover="this.style.background='#fff4f4'" onmouseout="this.style.background='none'">👑 관리자 메뉴</a>\n                        '''
    layout = layout.replace('<a th:href="@{/mypage}"', admin_a + '<a th:href="@{/mypage}"')

    with open('src/main/resources/templates/fragments/layout.html', 'w', encoding='utf-8') as f:
        f.write(layout)
        
    print("layout updated")
except Exception as e:
    import traceback
    traceback.print_exc()

# 2. Update index.html to move banner wrap
try:
    with open('src/main/resources/templates/index.html', 'r', encoding='utf-8') as f:
        html = f.read()

    # Extract the banner-wrap div
    # It starts with <div class="banner-wrap"> and ends with </div> before <!-- 5. 모임 보드 -->
    start = html.find('<!-- 4. 광고배너')
    if start == -1:
        # try find banner-wrap directly
        start = html.find('<div class="banner-wrap">')
    
    end = html.find('<!-- 5.', start)
    
    if start != -1 and end != -1:
        banner_code = html[start:end]
        html = html[:start] + html[end:] # remove from old position

        # Insert before search wrap -> <!-- 2. 검색창 -->
        search_start = html.find('<!-- 2.')
        if search_start != -1:
            html = html[:search_start] + banner_code + '\n  ' + html[search_start:]
            
        with open('src/main/resources/templates/index.html', 'w', encoding='utf-8') as f:
            f.write(html)
        print("index updated")
except Exception as e:
    import traceback
    traceback.print_exc()
