package com.storeledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StaticResourceTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 화면_파일은_매번_바뀌었는지_확인하게_한다() throws Exception {
        // 업데이트 후 브라우저가 옛 app.js 를 캐시에서 꺼내 쓰면 새 서버에 옛 형식으로 저장해 버린다
        mvc.perform(get("/app.js")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-cache"));
    }
}
