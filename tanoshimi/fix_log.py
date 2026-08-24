import codecs

with codecs.open('src/main/java/net/datasa/tanoshimi/exception/GlobalExceptionHandler.java', 'r', 'utf-8-sig') as f:
    text = f.read()

text = text.replace('public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {',
                    'public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) { log.error("BusinessException: {}", e.getMessage(), e);')
text = text.replace('public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {',
                    'public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) { log.error("Validation Error: {}", e.getMessage(), e);')
text = text.replace('public class GlobalExceptionHandler {',
                    """public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled Exception", e);
        return ResponseEntity.internalServerError().body(ApiResponse.fail("Server Error"));
    }
""")

with codecs.open('src/main/java/net/datasa/tanoshimi/exception/GlobalExceptionHandler.java', 'w', 'utf-8') as f:
    f.write(text)
