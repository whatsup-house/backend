package com.whatsuphouse.backend.domain.review.admin.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewHomeOrderRequest {

    @NotEmpty
    private List<@Valid ReviewHomeOrderItemRequest> items;
}
