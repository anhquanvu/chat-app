package com.revotech.chatapp.model.enums;

public enum ReactionType {
    LIKE("👍"), LOVE("❤️"), LAUGH("😂"), CRY("😢"), ANGRY("😡"), WOW("😮");

    private final String emoji;
    ReactionType(String emoji) { this.emoji = emoji; }
    public String getEmoji() { return emoji; }
}
