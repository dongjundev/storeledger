package com.storeledger;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 상품 → 판매 기록 → 집계까지 API 흐름 검증. 각 테스트는 트랜잭션 롤백으로 서로 독립. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SalesApiTest {

    // 판매가 19,900 / 원가 8,000 / 택배비 3,000 / 기타 500 / 수수료 3.63% + 2.00% → 수수료 1,120, 개당 마진 7,280
    private static final String MUG = """
            {"name":"머그컵","sellingPrice":19900,"costPrice":8000,"shippingCost":3000,"otherCost":500,
             "orderFeeRate":3.63,"salesFeeRate":2.00}
            """;

    @Autowired
    MockMvc mvc;

    @Test
    void 상품을_등록하면_수수료와_마진이_계산된다() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(MUG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.buyerShippingFee").value(0))
                .andExpect(jsonPath("$.orderFeeRate").value(3.63))
                .andExpect(jsonPath("$.salesFeeRate").value(2.0))
                .andExpect(jsonPath("$.fee").value(1120))
                .andExpect(jsonPath("$.margin").value(7280))
                .andExpect(jsonPath("$.marginRate").value(36.6));
    }

    @Test
    void 수수료율을_생략하면_주문관리와_판매_기본값을_쓴다() throws Exception {
        // 기본값 1.95% + 3.00%: 19,900 × 4.95% = 985.05 → 985, 마진 19,900 − 8,000 − 3,000 − 500 − 985 = 7,415
        mvc.perform(post("/api/margin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellingPrice\":19900,\"costPrice\":8000,\"shippingCost\":3000,\"otherCost\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(985))
                .andExpect(jsonPath("$.margin").value(7415))
                .andExpect(jsonPath("$.marginRate").value(37.3));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"볼펜\",\"sellingPrice\":3000,\"costPrice\":800}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderFeeRate").value(1.95))
                .andExpect(jsonPath("$.salesFeeRate").value(3.0));
    }

    @Test
    void 구매자_배송비는_이익에만_들어가고_매출에는_들어가지_않는다() throws Exception {
        // 머그컵을 구매자 배송비 3,000원으로: 수수료 1,120 + 109 = 1,229, 개당 마진 10,171
        String body = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(MUG.replace("\"otherCost\":500", "\"otherCost\":500,\"buyerShippingFee\":3000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.buyerShippingFee").value(3000))
                .andExpect(jsonPath("$.fee").value(1229))
                .andExpect(jsonPath("$.margin").value(10171))
                .andReturn().getResponse().getContentAsString();
        long productId = JsonPath.parse(body).read("$.id", Long.class);

        createSale(productId, "2026-09-04", 2, null);

        mvc.perform(get("/api/sales/summary").param("period", "DAILY").param("from", "2026-09-04").param("to", "2026-09-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(39800))   // 19,900 × 2 (배송비 제외)
                .andExpect(jsonPath("$.totalProfit").value(20342));   // 10,171 × 2
    }

    @Test
    void 판매_단가를_생략하면_상품_판매가를_사용한다() throws Exception {
        long productId = createProduct();

        mvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"saleDate\":\"2026-09-04\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productName").value("머그컵"))
                .andExpect(jsonPath("$.unitPrice").value(19900))
                .andExpect(jsonPath("$.revenue").value(39800))
                .andExpect(jsonPath("$.profit").value(14560));
    }

    @Test
    void 일별_집계는_판매가_없는_날도_0으로_채운다() throws Exception {
        long productId = createProduct();
        createSale(productId, "2026-09-01", 2, null);
        createSale(productId, "2026-09-04", 1, 15000); // 할인 판매: 15,000 − 8,000 − 3,000 − 500 − 845 = 2,655

        mvc.perform(get("/api/sales/summary").param("period", "DAILY").param("from", "2026-09-01").param("to", "2026-09-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("DAILY"))
                .andExpect(jsonPath("$.points.length()").value(4))
                .andExpect(jsonPath("$.points[0].label").value("2026-09-01"))
                .andExpect(jsonPath("$.points[0].revenue").value(39800))
                .andExpect(jsonPath("$.points[0].profit").value(14560))
                .andExpect(jsonPath("$.points[1].revenue").value(0))
                .andExpect(jsonPath("$.points[2].revenue").value(0))
                .andExpect(jsonPath("$.points[3].revenue").value(15000))
                .andExpect(jsonPath("$.points[3].profit").value(2655))
                .andExpect(jsonPath("$.totalRevenue").value(54800))
                .andExpect(jsonPath("$.totalProfit").value(17215))
                .andExpect(jsonPath("$.totalQuantity").value(3));
    }

    @Test
    void 주별_월별_집계는_구간을_조회_범위로_잘라낸다() throws Exception {
        long productId = createProduct();
        createSale(productId, "2026-08-25", 4, null); // 화요일, 2026-W35 (8/24~8/30)
        createSale(productId, "2026-09-01", 2, null); // 2026-W36 (8/31~9/6)
        createSale(productId, "2026-09-04", 1, null);

        mvc.perform(get("/api/sales/summary").param("period", "WEEKLY").param("from", "2026-08-25").param("to", "2026-09-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].label").value("2026-W35"))
                .andExpect(jsonPath("$.points[0].start").value("2026-08-25"))
                .andExpect(jsonPath("$.points[0].end").value("2026-08-30"))
                .andExpect(jsonPath("$.points[0].revenue").value(79600))
                .andExpect(jsonPath("$.points[0].quantity").value(4))
                .andExpect(jsonPath("$.points[1].label").value("2026-W36"))
                .andExpect(jsonPath("$.points[1].start").value("2026-08-31"))
                .andExpect(jsonPath("$.points[1].end").value("2026-09-04"))
                .andExpect(jsonPath("$.points[1].revenue").value(59700))
                .andExpect(jsonPath("$.points[1].quantity").value(3));

        mvc.perform(get("/api/sales/summary").param("period", "MONTHLY").param("from", "2026-08-01").param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].label").value("2026-08"))
                .andExpect(jsonPath("$.points[0].revenue").value(79600))
                .andExpect(jsonPath("$.points[1].label").value("2026-09"))
                .andExpect(jsonPath("$.points[1].revenue").value(59700))
                .andExpect(jsonPath("$.totalRevenue").value(139300));
    }

    @Test
    void 연도별_기본_범위는_최근_5년이다() throws Exception {
        mvc.perform(get("/api/sales/summary").param("period", "YEARLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(5));
    }

    @Test
    void 구간이_1000개를_넘는_조회는_400이고_큰_단위로는_볼_수_있다() throws Exception {
        // 2024-01-01 ~ 2026-09-26 은 1,000일, 하루 더 늘리면 1,001일
        mvc.perform(get("/api/sales/summary").param("period", "DAILY").param("from", "2024-01-01").param("to", "2026-09-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(1000));
        mvc.perform(get("/api/sales/summary").param("period", "DAILY").param("from", "2024-01-01").param("to", "2026-09-27"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("조회 기간이 너무 깁니다.")));
        // 날짜를 입력하다 만 연도(0002년)로 들어온 요청
        mvc.perform(get("/api/sales/summary").param("period", "DAILY").param("from", "0002-09-12").param("to", "2026-09-12"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/sales/summary").param("period", "MONTHLY").param("from", "2000-01-01").param("to", "2026-09-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(321));
    }

    @Test
    void 금액은_1억원_이하_수수료율은_소수_둘째_자리까지만_받는다() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"고가\",\"sellingPrice\":1500000000,\"costPrice\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("sellingPrice: 판매가는 1억 원 이하여야 합니다"));
        mvc.perform(post("/api/margin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellingPrice\":10000,\"costPrice\":1,\"orderFeeRate\":1.947}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("orderFeeRate: 주문관리 수수료율은 소수 둘째 자리까지 입력하세요"));
    }

    @Test
    void 모르는_필드나_100퍼센트를_넘는_수수료율_합계는_400() throws Exception {
        // 업데이트 전에 캐시된 옛 화면은 feeRate 를 보낸다. 조용히 무시하면 수수료율이 기본값으로 바뀐 채 저장된다
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"옛 화면\",\"sellingPrice\":10000,\"costPrice\":1,\"feeRate\":6.6}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/margin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellingPrice\":1000,\"costPrice\":1,\"orderFeeRate\":60,\"salesFeeRate\":41}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("합계는 100% 이하")));
    }

    @Test
    void 시작일이_종료일보다_늦으면_400() throws Exception {
        mvc.perform(get("/api/sales/summary").param("from", "2026-09-05").param("to", "2026-09-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("시작일이 종료일보다 늦을 수 없습니다."));
    }

    @Test
    void 잘못된_상품_요청은_400과_필드별_메시지를_돌려준다() throws Exception {
        String body = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"sellingPrice\":-1,\"costPrice\":100}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        String detail = JsonPath.read(body, "$.detail");
        assertTrue(detail.contains("name: 상품명을 입력하세요"), detail);
        assertTrue(detail.contains("sellingPrice: 판매가는 0 이상이어야 합니다"), detail);
    }

    @Test
    void 판매_기록이_있는_상품은_삭제할_수_없다() throws Exception {
        long productId = createProduct();
        createSale(productId, "2026-09-04", 1, null);

        mvc.perform(delete("/api/products/" + productId))
                .andExpect(status().isConflict());

        String sales = mvc.perform(get("/api/sales").param("from", "2026-09-04").param("to", "2026-09-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        long saleId = JsonPath.parse(sales).read("$[0].id", Long.class);

        mvc.perform(delete("/api/sales/" + saleId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/products/" + productId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + productId)).andExpect(status().isNotFound());
    }

    private long createProduct() throws Exception {
        String body = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(MUG))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.id", Long.class);
    }

    private void createSale(long productId, String date, int quantity, Integer unitPrice) throws Exception {
        String json = "{\"productId\":" + productId + ",\"saleDate\":\"" + date + "\",\"quantity\":" + quantity
                + (unitPrice == null ? "" : ",\"unitPrice\":" + unitPrice) + "}";
        mvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }
}
