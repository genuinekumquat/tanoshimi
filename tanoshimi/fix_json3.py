import sys

def fix_file(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    # We need to find the loop where newSched is parsed
    old_code = """            if (newSched != null && newSched.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : newSched) {
                    if ("package_default".equals(node.path("source").asText())) {
                        continue;
                    }
                    Long aid = node.path("activityId").isNull() ? null : node.path("activityId").asLong();
                    ScheduleItemRequest req = new ScheduleItemRequest(
                            node.path("dayIndex").asInt(),
                            node.path("startMinute").asInt(),
                            node.path("durationMinute").asInt(),
                            aid,"""
                            
    new_code = """            if (newSched != null && newSched.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : newSched) {
                    if ("package_default".equals(node.path("source").asText())) {
                        continue;
                    }
                    String nodeSource = node.path("source").asText("custom");
                    Long aid = node.path("activityId").isNull() ? null : node.path("activityId").asLong();
                    if ("custom".equals(nodeSource)) {
                        aid = null;
                    } else if (aid != null) {
                        Long mappedAid = null;
                        for (TripScheduleItemEntity it : allItems) {
                            if (it.getId().equals(aid) && it.getActivity() != null) {
                                mappedAid = it.getActivity().getId();
                                break;
                            }
                        }
                        aid = mappedAid;
                    }

                    ScheduleItemRequest req = new ScheduleItemRequest(
                            node.path("dayIndex").asInt(),
                            node.path("startMinute").asInt(),
                            node.path("durationMinute").asInt(),
                            aid,"""

    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Fixed {filename}")
    else:
        print(f"Code not found in {filename}")

fix_file("src/main/java/net/datasa/tanoshimi/controller/PlannerController.java")
