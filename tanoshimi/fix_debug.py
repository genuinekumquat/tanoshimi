import sys
import re

def fix_geminichatclient(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # Change the return statement in the catch block to return the error message
    old_catch = """        } catch (Exception e) {
            log.error("Gemini API 호출 실패: " + (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException ? ((org.springframework.web.reactive.function.client.WebClientResponseException)e).getResponseBodyAsString() : e.getMessage()), e);
        }
        return "어라, 지금 통신이 잘 안 되네... 잠시 후 다시 말 걸어줄래? 📡";"""
        
    new_catch = """        } catch (Exception e) {
            String errMsg = e instanceof org.springframework.web.reactive.function.client.WebClientResponseException ? ((org.springframework.web.reactive.function.client.WebClientResponseException)e).getResponseBodyAsString() : e.getMessage();
            log.error("Gemini API 호출 실패: " + errMsg, e);
            return "디버그 안됨: " + errMsg;
        }
        return "어라, 지금 통신이 잘 안 되네... 잠시 후 다시 말 걸어줄래? 📡";"""
        
    if "return \"어라, 지금 통신이 잘 안 되네... 잠시 후 다시 말 걸어줄래? 📡\";" in content:
        content = content.replace(old_catch, new_catch)
        
    # Also I'll check if maybe the problem is model config
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {filename}")

fix_geminichatclient("src/main/java/net/datasa/tanoshimi/util/GeminiChatClient.java")
