package net.datasa.tanoshimi.domain.entity;

public enum Role {
    user("ROLE_USER"), admin("ROLE_ADMIN");

    private final String key;
    Role(String key) { this.key = key; }
    public String key() { return key; }
}
