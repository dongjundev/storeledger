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

    // 판매가 19,900 / 원가 8,000 / 배송비 3,000 / 기타 500 / 수수료 5.63% → 수수료 1,120, 개당 마진 7,280
    private static final String MUG = """
            {"name":"머그컵","sellingPrice":19900,"costPrice":8000,"shippingCost":3000,"otherCost":500,"feeRate":5.63}
            """;

    @Autowired
    MockMvc mvc;

    @Test
    void 상품을_등록하면_수수료와_마진이_계산된다() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(MUG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fee").value(1120))
                .andExpect(jsonPath("$.margin").value(7280))
                .andExpect(jsonPath("$.marginRate").value(36.6));
    }

    @Test
    void 마진_미리보기는_수수료율_생략시_기본값을_쓴다() throws Exception {
        mvc.perform(post("/api/margin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellingPrice\":19900,\"costPrice\":8000,\"shippingCost\":3000,\"otherCost\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(1120))
                .andExpect(jsonPath("$.margin").value(7280))
                .andExpect(jsonPath("$.marginRate").value(36.6));
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
