import sys

# 1. Update TripScheduleItemEntity
file1 = "src/main/java/net/datasa/tanoshimi/domain/entity/TripScheduleItemEntity.java"
with open(file1, 'r', encoding='utf-8') as f:
    content = f.read()

# Add color field
if 'private String color;' not in content:
    content = content.replace('private String memo;', 'private String memo;\n\n    @Column(length = 20)\n    private String color;')

    # Update builder
    content = content.replace(
        'String title, String memo, Integer priceKrw, Integer priceJpy, UserEntity addedBy',
        'String title, String memo, String color, Integer priceKrw, Integer priceJpy, UserEntity addedBy'
    )
    content = content.replace(
        'this.memo = memo;',
        'this.memo = memo;\n        this.color = color;'
    )
    content = content.replace(
        'public void rename(String title, String memo) {',
        'public void rename(String title, String memo, String color) {\n        this.color = color;'
    )
    with open(file1, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated {file1}")

# 2. Update ScheduleItemRequest
file2 = "src/main/java/net/datasa/tanoshimi/domain/dto/ScheduleItemRequest.java"
with open(file2, 'r', encoding='utf-8') as f:
    content = f.read()

if 'String color' not in content:
    content = content.replace(
        'String title,             // custom 이면 사용자가 직접 입력',
        'String title,             // custom 이면 사용자가 직접 입력\n        String color,             // 배경색 지정 HEX\n'
    )
    # The record fields should be rewritten safely just in case
    # The original is:
    #         String title,             // custom 이면 사용자가 직접 입력
    #         String memo
    # ) {
    content = content.replace(
        'String memo\n) {',
        'String memo,\n        String color\n) {'
    )
    with open(file2, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated {file2}")

# 3. Update ScheduleItemView
file3 = "src/main/java/net/datasa/tanoshimi/domain/dto/ScheduleItemView.java"
with open(file3, 'r', encoding='utf-8') as f:
    content = f.read()

if 'String color' not in content:
    content = content.replace(
        'String source, String title, String memo, int priceKrw, int priceJpy',
        'String source, String title, String memo, String color, int priceKrw, int priceJpy'
    )
    with open(file3, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated {file3}")

# 4. Update TripPlannerService
file4 = "src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java"
with open(file4, 'r', encoding='utf-8') as f:
    content = f.read()

if 'i.getMemo(), i.getColor()' not in content:
    content = content.replace(
        'i.getSource().name(), i.getTitle(), i.getMemo(), i.getPriceKrw(), i.getPriceJpy()',
        'i.getSource().name(), i.getTitle(), i.getMemo(), i.getColor(), i.getPriceKrw(), i.getPriceJpy()'
    )
    content = content.replace(
        '.title(title == null || title.isBlank() ? "이름없는 일정" : title)\n                .memo(req.memo())',
        '.title(title == null || title.isBlank() ? "이름없는 일정" : title)\n                .memo(req.memo())\n                .color(req.color())'
    )
    with open(file4, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated {file4}")

# 5. Update PlannerController
file5 = "src/main/java/net/datasa/tanoshimi/controller/PlannerController.java"
with open(file5, 'r', encoding='utf-8') as f:
    content = f.read()

old_json = '"title": "...", "source": "...", "activityId": null}, ...]}\n'
new_json = '"title": "...", "memo": "...", "color": "#FFA500", "source": "...", "activityId": null}, ...]}\n'
if old_json in content:
    content = content.replace(old_json, new_json)

# Update node extraction mapping for generic request
old_map = 'node.path("memo").isNull() ? null : node.path("memo").asText()'
new_map = 'node.path("memo").isNull() ? null : node.path("memo").asText(), node.path("color").isNull() ? null : node.path("color").asText()'
if old_map in content:
    content = content.replace(old_map, new_map)

# Replace the specific mapping context
original_creation = """                    ScheduleItemRequest req = new ScheduleItemRequest(
                            node.path("dayIndex").asInt(),
                            node.path("startMinute").asInt(),
                            node.path("durationMinute").asInt(),
                            aid,
                            node.path("title").asText(),
                            node.path("memo").isNull() ? null : node.path("memo").asText()
                    );"""
new_creation = """                    ScheduleItemRequest req = new ScheduleItemRequest(
                            node.path("dayIndex").asInt(),
                            node.path("startMinute").asInt(),
                            node.path("durationMinute").asInt(),
                            aid,
                            node.path("title").asText(),
                            node.path("memo").isNull() ? null : node.path("memo").asText(),
                            node.path("color").isNull() ? null : node.path("color").asText()
                    );"""
if original_creation in content:
    content = content.replace(original_creation, new_creation)
    
# also the regular item creation
orig2 = """    @PostMapping("/api/planner/{scheduleId}/items")
    @ResponseBody
    public ApiResponse<Long> addItem(@PathVariable Long scheduleId, @Valid @RequestBody ScheduleItemRequest request,"""
# no change needed here because it's automatic binding

# also prompt context update
orig3 = 'item.dayIndex(), time, item.title(), isFixed, item.id(), item.source()));'
new3 = 'item.dayIndex(), time, item.title() + " (Memo:" + item.memo() + ")", isFixed, item.id(), item.source()));'
if orig3 in content:
    content = content.replace(orig3, new3)

with open(file5, 'w', encoding='utf-8') as f:
    f.write(content)
print(f"Updated {file5}")
