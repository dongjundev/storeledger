package com.storeledger;

import com.jayway.jsonpath.JsonPath;
import com.storeledger.category.Category;
import com.storeledger.category.CategoryRepository;
import com.storeledger.common.DataSeedRepository;
import com.storeledger.category.DefaultCategorySeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 카테고리 추가·수정·삭제와 상품 연결. 각 테스트는 트랜잭션 롤백으로 서로 독립. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CategoryRepository categories;

    @Autowired
    DefaultCategorySeeder seeder;

    @Autowired
    DataSeedRepository seeds;

    @Test
    void 기본_카테고리가_입력한_순서대로_들어있다() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains(DefaultCategorySeeder.NAMES.toArray())))
                .andExpect(jsonPath("$[0].productCount").value(0));
    }

    @Test
    void 기본_카테고리는_지워도_다시_생기지_않는다() {
        categories.deleteAll();

        seeder.run(null); // 재시작과 같은 상황

        assertEquals(0, categories.count());
    }

    @Test
    void 기록이_없는_DB에_기본_이름이_일부_있어도_시작이_실패하지_않는다() {
        // 복원했거나 옮겨 온 DB: 카테고리 Kitchen 은 있는데 기본값을 넣었다는 기록이 없다
        seeds.deleteAllInBatch();       // 일괄 삭제는 바로 실행된다 (Hibernate는 보통 삽입을 삭제보다 먼저 보낸다)
        categories.deleteAllInBatch();
        categories.save(new Category("Kitchen"));

        seeder.run(null);

        assertEquals(DefaultCategorySeeder.NAMES.size(), categories.count());
    }

    @Test
    void 카테고리를_추가_수정_삭제한다() throws Exception {
        long id = createCategory("  문구  ");

        mvc.perform(put("/api/categories/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"문구·사무\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("문구·사무"));

        mvc.perform(delete("/api/categories/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$[?(@.name == '문구·사무')]").isEmpty());
    }

    @Test
    void 이름은_대소문자_구분없이_겹칠_수_없다() throws Exception {
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"pet\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("이미 있는 카테고리입니다: pet"));

        long id = createCategory("문구");
        mvc.perform(put("/api/categories/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"KITCHEN\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/categories/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"문구\"}"))
                .andExpect(status().isOk()); // 자기 이름 그대로 저장은 허용

        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 상품에_카테고리를_지정하고_카테고리를_지우면_미분류가_된다() throws Exception {
        long stationery = createCategory("문구");
        long props = createCategory("소품");

        String body = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"볼펜","categoryId":%d,"sellingPrice":3000,"costPrice":800}
                        """.formatted(stationery)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(stationery))
                .andExpect(jsonPath("$.categoryName").value("문구"))
                .andReturn().getResponse().getContentAsString();
        long productId = JsonPath.parse(body).read("$.id", Long.class);

        mvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$[?(@.name == '문구')].productCount").value(1));

        mvc.perform(put("/api/products/" + productId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"볼펜","categoryId":%d,"sellingPrice":3000,"costPrice":800}
                        """.formatted(props)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("소품"));

        mvc.perform(delete("/api/categories/" + props)).andExpect(status().isNoContent());

        mvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId", nullValue()))
                .andExpect(jsonPath("$.categoryName", nullValue()));
    }

    @Test
    void 없는_카테고리로_상품을_등록하면_404() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"볼펜\",\"categoryId\":999999,\"sellingPrice\":3000,\"costPrice\":800}"))
                .andExpect(status().isNotFound());
    }

    private long createCategory(String name) throws Exception {
        String body = mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name.strip()))
                .andExpect(jsonPath("$.productCount").value(0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.id", Long.class);
    }
}
