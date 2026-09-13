package com.storeledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** desktop 프로필이 데이터·로그를 app.home 아래에 두는지 검증한다 (Mac 앱 실행 모드). */
// Gradle 이 실행마다 빈 폴더를 넘긴다. IDE 에서 바로 돌리면 임시 폴더를 쓴다
@SpringBootTest(properties = "app.home=${storeledger.desktop-test.home:${java.io.tmpdir}/storeledger-desktop-test}")
@ActiveProfiles("desktop")
@AutoConfigureMockMvc
class DesktopProfileTest {

    @Autowired
    Environment env;

    @Autowired
    MockMvc mvc;

    @Test
    void 데이터와_로그를_app_home_아래에_둔다() {
        String appHome = env.getProperty("app.home");
        Path home = Path.of(appHome);

        assertEquals("jdbc:h2:file:" + appHome + "/data/storeledger", env.getProperty("spring.datasource.url"));
        assertTrue(Files.exists(home.resolve("data/storeledger.mv.db")), "H2 파일이 app.home/data 에 생겨야 한다");
        assertTrue(Files.exists(home.resolve("logs/app.log")), "로그가 app.home/logs 에 생겨야 한다");
        assertEquals("127.0.0.1", env.getProperty("server.address"));
    }

    @Test
    void localhost로_온_요청만_받고_H2_콘솔은_꺼져_있다() throws Exception {
        mvc.perform(get("/api/settings")).andExpect(status().isOk());                                  // Host: localhost
        mvc.perform(get("/api/settings").header("Host", "127.0.0.1:28080")).andExpect(status().isOk());
        mvc.perform(get("/api/settings").header("Host", "LOCALHOST:28080")).andExpect(status().isOk());          // 대소문자 무시
        mvc.perform(get("/api/settings").header("Host", "evil.example:28080")).andExpect(status().isForbidden()); // DNS 리바인딩
        // MockMvc 는 H2 콘솔 서블릿을 거치지 않아 404 만 보이므로, 설정값 자체를 확인한다
        assertEquals("false", env.getProperty("spring.h2.console.enabled"));
    }
}
