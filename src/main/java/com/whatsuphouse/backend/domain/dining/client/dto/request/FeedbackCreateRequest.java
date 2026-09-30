package com.whatsuphouse.backend.domain.dining.client.dto.request;

import com.whatsuphouse.backend.domain.dining.enums.PeerPreferenceKind;
import com.whatsuphouse.backend.domain.dining.enums.RejoinIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class FeedbackCreateRequest {

    @NotNull
    @Min(1)
    @Max(5)
    @Schema(description = "테이블 만족도 1~5", example = "5")
    private Integer tableScore;

    @NotNull
    @Min(1)
    @Max(5)
    @Schema(description = "대화 만족도 1~5", example = "4")
    private Integer talkScore;

    @NotNull
    @Min(1)
    @Max(5)
    @Schema(description = "식당 만족도 1~5", example = "4")
    private Integer venueScore;

    @NotNull
    @Schema(description = "재참여 의향", example = "YES")
    private RejoinIntent rejoinIntent;

    @Size(max = 2000)
    @Schema(description = "자유 의견", example = "대화가 즐거웠어요", nullable = true)
    private String comment;

    // 같은 테이블의 다른 멤버만, 한 사람당 한 번. 운영자·매칭 전용이라 상대에게 알리지 않는다.
    @Valid
    @Size(max = 20)
    @Schema(description = "사람별 선호(선택). 같은 테이블의 다른 멤버만 한 번씩", nullable = true)
    private List<Peer> peers;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Peer {

        @NotNull
        @Schema(description = "같은 테이블 멤버의 회원 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID userId;

        @NotNull
        @Schema(description = "AGAIN=다시 만나고 싶음, AVOID=같은 테이블 원치 않음", example = "AGAIN")
        private PeerPreferenceKind kind;
    }
}
