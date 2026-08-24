package net.datasa.tanoshimi.domain.dto;

public record CommentRequest(
    String content,
    Long parentId
) {
}