import re
import os

# 1. PartyEntity fix
path = 'src/main/java/net/datasa/tanoshimi/domain/entity/PartyEntity.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

if 'private boolean blinded' not in text:
    target = 'private byte capacity;'
    text = text.replace(target, target + '\n\n    @Column(nullable = false, columnDefinition = "boolean default false")\n    private boolean blinded = false;\n\n    public boolean isBlinded() { return blinded; }\n    public void blind() { this.blinded = true; }')
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)

# 2. FileStorageService fix (Variable original is already defined)
path = 'src/main/java/net/datasa/tanoshimi/service/FileStorageService.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# I replaces String original = file.getOriginalFilename(); ?
text = text.replace('String original = file.getOriginalFilename();', 'String originalStr = file.getOriginalFilename();')
text = text.replace('extensionOf(original)', 'extensionOf(originalStr)')

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

# 3. HomeController fix
path = 'src/main/java/net/datasa/tanoshimi/controller/HomeController.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('partyRepository.findByStatusOrderByDepartureDateAsc', 'partyRepository.findByStatusAndBlindedFalseOrderByDepartureDateAsc')

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

print("Errors fixed!")