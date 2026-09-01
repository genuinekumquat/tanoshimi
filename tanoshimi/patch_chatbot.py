import re

file_path = r'C:\Users\kean4\Documents\GitHub\tanoshimi\tanoshimi\src\main\java\net\datasa\tanoshimi\service\ChatbotActivityService.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add import for RecommendationDto
content = content.replace("import net.datasa.tanoshimi.util.GeminiClient;", "import net.datasa.tanoshimi.util.GeminiClient;\nimport net.datasa.tanoshimi.domain.dto.RecommendationDto;\nimport com.fasterxml.jackson.databind.ObjectMapper;\nimport com.fasterxml.jackson.core.type.TypeReference;")

# Change return types
content = content.replace("public List<ActivityEntity> recommend(String region, LocalDate date, String keyword, boolean todayIsBadWeather)", "public List<RecommendationDto> recommend(String region, LocalDate date, String keyword, boolean todayIsBadWeather)")
content = content.replace("public List<ActivityEntity> recommend(String region, LocalDate date, String keyword, String pastStyleTags, boolean todayIsBadWeather)", "public List<RecommendationDto> recommend(String region, LocalDate date, String keyword, String pastStyleTags, boolean todayIsBadWeather)")

# Rewrite the main recommend method
replacement = """    @Transactional
    public List<RecommendationDto> recommend(String region, LocalDate date, String keyword, String pastStyleTags, boolean todayIsBadWeather) {
        activityRepository.findByRegionAndStatus(region, ActiveStatus.active).stream()
                .filter(ActivityEntity::needsVenueTypeJudgement)
                .forEach(this::judgeAndCacheVenueType);
        
        List<ActivityEntity> pool;
        if (todayIsBadWeather) {
            pool = activityRepository.findByRegionAndVenueTypeAndStatus(region, VenueType.indoor, ActiveStatus.active);
            pool = new ArrayList<>(pool);
            pool.addAll(activityRepository.findByRegionAndVenueTypeAndStatus(region, VenueType.mixed, ActiveStatus.active));
        } else {
            pool = activityRepository.findByRegionAndStatus(region, ActiveStatus.active);
        }
        
        if (pool.isEmpty()) {
            pool = activityRepository.findByStatus(ActiveStatus.active);
        }
        
        // Return existing items if no keyword specified
        if (keyword == null || keyword.isBlank()) {
            return pool.stream().limit(5).map(a -> new RecommendationDto("recommend", a.getId(), a.getTitle(), a.getDurationMin(), a.getPriceKrw(), a.getDescription())).toList();
        }

        try {
            StringBuilder poolContext = new StringBuilder();
            pool.stream().limit(20).forEach(a -> {
                poolContext.append(String.format("ID:%d, Title:%s, Duration:%d min, Desc:%s\\n", a.getId(), a.getTitle(), a.getDurationMin(), a.getDescription()));
            });

            String prompt = String.format(
                    "You are a helpful travel planner. User request: '%s'. " +
                    "Return a JSON array of up to 5 recommended schedule items. " +
                    "You can pick from the following existing items if relevant:\\n%s\\n" +
                    "If you use an existing item, set 'kind' to 'recommend', use its exact 'activityId', 'title', and 'durationMin'. " +
                    "If you want to suggest a completely new customized activity based on the user's prompt, set 'kind' to 'custom', 'activityId' to null, and invent a good 'title' and 'durationMin'. " +
                    "Output ONLY valid JSON array with keys: kind, activityId (number or null), title (string), durationMin (number). No markdown blocks.",
                    keyword, poolContext.toString()
            );
            
            String aiResponse = geminiClient.ask(prompt);
            String jsonRaw = aiResponse.trim();
            if (jsonRaw.startsWith("```json")) {
                jsonRaw = jsonRaw.substring(7);
                if (jsonRaw.endsWith("```")) {
                    jsonRaw = jsonRaw.substring(0, jsonRaw.length() - 3);
                }
            } else if (jsonRaw.startsWith("```")) {
                jsonRaw = jsonRaw.substring(3);
                if (jsonRaw.endsWith("```")) {
                    jsonRaw = jsonRaw.substring(0, jsonRaw.length() - 3);
                }
            }
            jsonRaw = jsonRaw.trim();
            
            ObjectMapper mapper = new ObjectMapper();
            List<RecommendationDto> resp = mapper.readValue(jsonRaw, new TypeReference<List<RecommendationDto>>() {});
            return resp;
        } catch (Exception e) {
            log.error("AI recommendation parse error", e);
            return pool.stream().limit(5).map(a -> new RecommendationDto("recommend", a.getId(), a.getTitle(), a.getDurationMin(), a.getPriceKrw(), a.getDescription())).toList();
        }
    }"""

content = re.sub(
    r'@Transactional\s+public\s+List<RecommendationDto>\s+recommend.*?todayIsBadWeather\)\s*\{.*?(?=private\s+String\s+extractCoreTagWithGemini)',
    replacement + '\n    ',
    content,
    flags=re.DOTALL
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
