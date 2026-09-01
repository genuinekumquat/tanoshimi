import re

path = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/service/ChatbotActivityService.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# Replace the prompt to include the new requirement
old_prompt = """                    "You are a helpful travel planner. User request: '%s'. " +
                    "Return a JSON array of up to 5 recommended schedule items. " +
                    "You can pick from the following existing items if relevant:\\n%s\\n" +
                    "If you use an existing item, set 'kind' to 'recommend', use its exact 'activityId', 'title', and 'durationMin'. " +
                    "If you want to suggest a completely new customized activity based on the user's prompt, set 'kind' to 'custom', 'activityId' to null, and invent a good 'title' and 'durationMin'. " +
                    "Output ONLY valid JSON array with keys: kind, activityId (number or null), title (string), durationMin (number). No markdown blocks.","""

new_prompt = """                    "You are a helpful travel planner. User request: '%s'. " +
                    "Return a JSON array of exactly 5 recommended schedule items. " +
                    "Requirement: 2 or 3 items MUST be the most famous, representative must-visit spots. " +
                    "The remaining 2 or 3 items MUST be creative, varied, lesser-known, or unique spots that rotate randomly so if I ask again, I get different suggestions! " +
                    "Use Google Search to find real tourist information for this region. " +
                    "You can pick from these existing DB items if relevant:\\n%s\\n" +
                    "If using an existing item, set 'kind' to 'recommend', keeping its exact 'activityId', 'title', 'durationMin'. " +
                    "If you invent a new web-sourced activity, set 'kind' to 'custom', 'activityId' to null, and give it a good 'title' and 'durationMin'. " +
                    "Output ONLY a valid JSON array with keys: kind, activityId (number or null), title (string), durationMin (number). Strip markdown blocks.","""

text = text.replace(old_prompt, new_prompt)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)