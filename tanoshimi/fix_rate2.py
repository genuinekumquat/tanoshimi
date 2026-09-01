import sys
import re

def fix_rate_limit2(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    pattern = re.compile(r'\} catch \(Exception e\) \{.*?return "\{\\"briefing\\": \\"API 오류: " \+ errMsg \+ "\\", \\"newSchedule\\": \[\]\}";\s*\}', re.DOTALL)
    
    new_catch = """} catch (Exception e) {
            log.error("Gemini Real API call failed", e);
            String errMsg = e.getMessage().replace("\"", "'");
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                org.springframework.web.reactive.function.client.WebClientResponseException we = (org.springframework.web.reactive.function.client.WebClientResponseException) e;
                if (we.getStatusCode().value() == 429) {
                    return "{\\"briefing\\": \\"구글 할배가 화가 단단히 난 데스! (429 Rate Limit) 무료 API 한도를 초과해서 잠시 막힌 테치. 딱 1분만 숨 참고 다시 눌러보는 데스웅~\\", \\"newSchedule\\": []}";
                }
                errMsg = we.getResponseBodyAsString().replace("\"", "'").replace("\\n", " ");
            }
            return "{\\"briefing\\": \\"API 오류: " + errMsg + "\\", \\"newSchedule\\": []}";
        }"""
        
    content = pattern.sub(new_catch, content)
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
        
    print(f"Fixed {filename}")

fix_rate_limit2("src/main/java/net/datasa/tanoshimi/util/RealGeminiClient.java")
