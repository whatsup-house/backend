package com.whatsuphouse.backend.domain.matching.dto.request;

import com.whatsuphouse.backend.domain.matching.enums.ResolutionChoice;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionChooseRequest {

    @NotNull
    @Schema(description = "선택. TRANSFER=대체 회차 이동, KEEP_TICKET=이용권 보관(복원), REFUND=환불", example = "TRANSFER")
    private ResolutionChoice choice;

    @Schema(description = "옮길 회차 ID. TRANSFER일 때 필수이며 제안 회차 중 하나여야 한다",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", nullable = true)
    private UUID sessionId;
}
