import re

path = 'src/main/java/net/datasa/tanoshimi/domain/entity/UserEntity.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

if 'private float mannerScore' not in text:
    target = 'private LocalDate birthDate;'
    text = text.replace(target, target + '\n\n    @Column(name = "manner_score", nullable = false, columnDefinition = "float default 36.5")\n    private float mannerScore = 36.5f;')
    
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)
    print("UserEntity patched with mannerScore!")