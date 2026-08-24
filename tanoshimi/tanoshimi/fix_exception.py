import codecs

with codecs.open('src/main/java/net/datasa/tanoshimi/exception/GlobalExceptionHandler.java', 'r', 'utf-8-sig') as f:
    text = f.read()

import re
# Remove the one I added
text = re.sub(
    r'@ExceptionHandler\(Exception\.class\)\s*public ResponseEntity<ApiResponse<Void>> handleException\(Exception e\) \{\s*log\.error\("Unhandled Exception", e\);\s*return ResponseEntity\.internalServerError\(\)\.body\(ApiResponse\.fail\("Server Error"\)\);\s*\}',
    '', text
)

with codecs.open('src/main/java/net/datasa/tanoshimi/exception/GlobalExceptionHandler.java', 'w', 'utf-8') as f:
    f.write(text)
