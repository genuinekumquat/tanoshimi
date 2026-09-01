import sys

def fix_real_gemini(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # Change return "UNKNOWN"; to return error JSON
    old_missing_key = 'return "UNKNOWN";'
    new_missing_key = 'return "{\\"briefing\\": \\"Gemini API key not configured.\\", \\"newSchedule\\": []}";'
    
    if old_missing_key in content:
        content = content.replace(old_missing_key, new_missing_key, 1) # first one
    
    old_catch = """        } catch (Exception e) {
            log.error("Gemini Real API call failed", e);
            return "UNKNOWN";
        }"""
        
    new_catch = """        } catch (Exception e) {
            log.error("Gemini Real API call failed", e);
            String errMsg = e instanceof org.springframework.web.reactive.function.client.WebClientResponseException ?
                ((org.springframework.web.reactive.function.client.WebClientResponseException) e).getResponseBodyAsString().replace("\"", "'").replace("\\n", " ") :
                e.getMessage().replace("\"", "'");
            return "{\\"briefing\\": \\"API 오류: " + errMsg + "\\", \\"newSchedule\\": []}";
        }"""
        
    if old_catch in content:
        content = content.replace(old_catch, new_catch)
        
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_real_gemini("src/main/java/net/datasa/tanoshimi/util/RealGeminiClient.java")
