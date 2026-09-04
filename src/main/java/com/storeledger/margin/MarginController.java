package com.storeledger.margin;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/margin")
public class MarginController {

    /** 상품을 저장하지 않고 단가·마진만 계산한다 (상품 등록 폼의 실시간 미리보기용). */
    @PostMapping
    public MarginResult calculate(@Valid @RequestBody MarginRequest request) {
        return request.calculate();
    }
}
