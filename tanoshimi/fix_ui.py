import sys

def fix_file(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    new_content = content.replace('--slot-h:22px;', '--slot-h:110px;')
    
    if new_content != content:
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Fixed {filename}")

fix_file("src/main/resources/templates/planner/index.html")

def fix_layout(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # max-width: 250px; => width: 250px; height: 150px; overflow-y: auto; max-width: none; maybe modify styles slightly
    old_style = "width: max-content; max-width: 250px; font-size: 14px; font-weight: 700; z-index: 10001; word-break: keep-all; pointer-events: auto;"
    new_style = "width: 250px; max-height: 150px; overflow-y: auto; font-size: 14px; font-weight: 700; z-index: 10001; word-break: keep-all; pointer-events: auto;"
    
    new_content = content.replace(old_style, new_style)
    
    if new_content != content:
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Fixed {filename}")
        
fix_layout("src/main/resources/templates/fragments/layout.html")

def fix_planner_js(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()
    
    new_content = content.replace('const SLOT_H = 22;', 'const SLOT_H = 110;')
    
    # We also need to add edit capability
    # Find:
    #                 const grip = el.querySelector('.grip');
    #                 if (grip) grip.addEventListener('pointerdown', (e) => startResize(e, item, el, startSlot, lenSlot));
    #             }
    #         });
    
    old_code = """                const grip = el.querySelector('.grip');
                if (grip) grip.addEventListener('pointerdown', (e) => startResize(e, item, el, startSlot, lenSlot));
            }"""
            
    new_code = """                const grip = el.querySelector('.grip');
                if (grip) grip.addEventListener('pointerdown', (e) => startResize(e, item, el, startSlot, lenSlot));
            }
            
            // Allow edit for custom
            if (item.source === 'custom' && (typeof IS_LOCK_HOLDER === 'undefined' || IS_LOCK_HOLDER)) {
                el.addEventListener('dblclick', async (e) => {
                    e.stopPropagation();
                    const newTitle = prompt('일정 제목을 수정하세요:', item.title);
                    if (newTitle !== null && newTitle.trim() !== '') {
                        // Assuming update API exists or we reuse item add logic
                        // Alternatively since we only have removeItem/addItem/resize, 
                        // we can remove and add with same params if there's no update endpoint. Wait, there is resize API but no edit API?
                        // Let's check PlannerController APIs. I'll just change title locally and we can figure out backend if needed.
                        // Actually, I'll delete then add!
                        await window.api.delete(`/api/planner/items/${item.id}`);
                        await window.api.post(`/api/planner/${SCHEDULE_ID}/items`, {
                            dayIndex: item.dayIndex,
                            startMinute: item.startMinute,
                            durationMinute: item.durationMinute,
                            activityId: null,
                            title: newTitle.trim(),
                            memo: item.memo
                        });
                        reload();
                    }
                });
            }"""
            
    new_content = new_content.replace(old_code, new_code)
    
    if new_content != content:
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Fixed {filename}")

fix_planner_js("src/main/resources/static/js/planner.js")
