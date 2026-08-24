package net.datasa.tanoshimi.domain.entity;

public enum UserStatus {
    active, suspended;
    public boolean isActive() { return this == active; }
}
