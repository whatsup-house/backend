package com.whatsuphouse.backend.domain.application.client.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ApplicationRequest {

    @NotNull
    @Valid
    private List<AnswerItem> answers;
}
