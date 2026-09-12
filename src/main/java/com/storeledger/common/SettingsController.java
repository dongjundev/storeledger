package com.storeledger.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면 표시용 설정. 상호는 git에 올라가지 않는 ./config/application.properties 에서 덮어쓴다. */
@RestController
public class SettingsController {

    public record Settings(String storeName) {
    }

    private final String storeName;

    public SettingsController(@Value("${app.store-name:Store Ledger}") String storeName) {
        this.storeName = storeName;
    }

    @GetMapping("/api/settings")
    public Settings settings() {
        return new Settings(storeName);
    }
}
