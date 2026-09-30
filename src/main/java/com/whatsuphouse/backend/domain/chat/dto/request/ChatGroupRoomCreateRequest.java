package com.whatsuphouse.backend.domain.chat.dto.request;

import com.whatsuphouse.backend.domain.chat.enums.ChatSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatGroupRoomCreateRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(example = "10월 재즈 게더링")
    private String name;

    @NotNull
    @Schema(description = "초대할 회원 ID (생성한 관리자는 자동 포함)", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
    private List<@NotNull UUID> memberIds;

    @Schema(description = "표시용 출처(선택)", example = "GATHERING")
    private ChatSourceType sourceType;

    @Schema(description = "표시용 출처 ID(선택)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID sourceId;
}
