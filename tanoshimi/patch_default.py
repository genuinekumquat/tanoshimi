for path in ['src/main/java/net/datasa/tanoshimi/domain/entity/PostEntity.java', 'src/main/java/net/datasa/tanoshimi/domain/entity/PartyEntity.java']:
    with open(path, 'r', encoding='utf-8') as f:
        text = f.read()
    text = text.replace('@Column(nullable = false)\n    private boolean blinded', '@Column(nullable = false, columnDefinition = "boolean default false")\n    private boolean blinded')
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)
