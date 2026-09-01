import re

with open('C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/service/ChatbotActivityService.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix 1: poolContext string
broken_pool = 'poolContext.append(String.format("ID:%d, Title:%s, Duration:%d min, Desc:%s\\n", a.getId(), a.getTitle(), a.getDurationMin(), a.getDescription()));'
# It was likely written with actual newlines. Let's find it with regex.

text = re.sub(r'Desc:%s\n"', r'Desc:%s\\n"', text)

# Fix 2: prompt string
text = re.sub(r'if relevant:\n%s\n', r'if relevant:\\n%s\\n', text)
text = re.sub(r'items with kind: "custom".\n', r'items with kind: "custom".\\n', text)
text = re.sub(r'json schema:\n', r'json schema:\\n', text)
text = re.sub(r'\}\]\n"', r'}]\\n"', text) # just in case

# Alternatively, I can just replace the whole ChatbotActivityService recommend method safely
method_safe = """
        try {
            StringBuilder poolContext = new StringBuilder();
            pool.stream().limit(20).forEach(a -> {
                poolContext.append(String.format("ID:%d, Title:%s, Duration:%d min, Desc:%s\\n", a.getId(), a.getTitle(), a.getDurationMin(), a.getDescription()));
            });

            String prompt = String.format(
                    "You are a travel planner. User request: '%s'. " +
                    "Return a JSON array of up to 5 items. Pick from these if relevant:\\n%s\\n" +
                    "Or invent your own items using kind: \\"custom\\".\\n" +
                    "Schema: [ {\\"kind\\":\\"recommend|custom\\", \\"referenceId\\": number(only if recommend), \\"title\\": \\"string\\", \\"durationMin\\": 30} ]",
                    keyword, poolContext.toString()
            );
"""
# Let's just fix NewLines in java strings globally if they aren't part of a text block