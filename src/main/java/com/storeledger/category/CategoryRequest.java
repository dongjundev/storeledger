package com.storeledger.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 카테고리 추가/수정 요청. 앞뒤 공백은 지운다. */
public record CategoryRequest(
        @NotBlank(message = "카테고리명을 입력하세요")
        @Size(max = 50, message = "카테고리명은 50자 이하여야 합니다") String name) {

    public CategoryRequest {
        if (name != null) name = name.strip();
    }
}
