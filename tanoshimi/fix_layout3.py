import codecs

with codecs.open('src/main/resources/templates/fragments/layout.html', 'r', 'utf-8-sig') as f:
    text = f.read()

patch_code = """            async patch(url, data={}) {
                const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
                const headers = { 'Content-Type': 'application/json' };
                if(csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
                try {
                    const r = await fetch(url, { method: 'PATCH', headers: headers, body: JSON.stringify(data) });
                    return await r.json();
                } catch(e) { return {success:false, message:'Network Error'}; }
            },
            async del(url) {
                const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
                const headers = {};
                if(csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
                try {
                    const r = await fetch(url, { method: 'DELETE', headers: headers });
                    return await r.json();
                } catch(e) { return {success:false, message:'Network Error'}; }
            }"""

# Since newlines can be \r\n, find the spot with regex
import re
text = re.sub(r'\} catch\(e\) \{ return \{success:false, message:\'Network Error\'\}; \}[\r\n\s]*\}[ \r\n]*\};',
              r"} catch(e) { return {success:false, message:'Network Error'}; }\n            },\n" + patch_code + "\n        };", text)


with codecs.open('src/main/resources/templates/fragments/layout.html', 'w', 'utf-8') as f:
    f.write(text)
