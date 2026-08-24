try:
    with open('src/main/resources/templates/index.html', 'r', encoding='utf-8') as f:
        html = f.read()

    # The line format: <a th:if="${b.targetUrl != null and b.targetUrl != ''}" th:href="${b.targetUrl}" target="_blank">
    old_a_tag = '<a th:if="${b.targetUrl != null and b.targetUrl != \'\'}" th:href="${b.targetUrl}" target="_blank">'
    new_a_tag = '<a th:if="${b.targetUrl != null and b.targetUrl != \'\'}" th:href="${b.targetUrl.startsWith(\'http\') ? b.targetUrl : \'https://\' + b.targetUrl}" target="_blank">'

    if old_a_tag in html:
        html = html.replace(old_a_tag, new_a_tag)
        with open('src/main/resources/templates/index.html', 'w', encoding='utf-8') as f:
            f.write(html)
        print("index.html tag updated")
    else:
        print("Tag not found!")
except Exception as e:
    import traceback
    traceback.print_exc()
