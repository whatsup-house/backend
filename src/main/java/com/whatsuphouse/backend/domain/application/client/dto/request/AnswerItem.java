package com.whatsuphouse.backend.domain.application.client.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

@Getter
public class AnswerItem {

    @NotNull
    private UUID questionId;

    @NotNull
    private Object value;
}
