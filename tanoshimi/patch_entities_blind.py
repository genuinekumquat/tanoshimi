import re

def patch_post_entity():
    path = 'src/main/java/net/datasa/tanoshimi/domain/entity/PostEntity.java'
    with open(path, 'r', encoding='utf-8') as f:
        text = f.read()

    if 'private boolean blinded' not in text:
        target = 'private int likeCount;'
        text = text.replace(target, target + '\n\n    @Column(nullable = false)\n    private boolean blinded = false;\n\n    public void blind() { this.blinded = true; }')
        
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)

def patch_party_entity():
    path = 'src/main/java/net/datasa/tanoshimi/domain/entity/PartyEntity.java'
    with open(path, 'r', encoding='utf-8') as f:
        text = f.read()

    if 'private boolean blinded' not in text:
        target = 'private int viewCount;'
        text = text.replace(target, target + '\n\n    @Column(nullable = false)\n    private boolean blinded = false;\n\n    public void blind() { this.blinded = true; }')
        
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)

patch_post_entity()
patch_party_entity()
print("Entities patched with blinded!")