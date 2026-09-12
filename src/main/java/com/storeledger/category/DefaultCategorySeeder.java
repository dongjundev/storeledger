package com.storeledger.category;

import com.storeledger.common.DataSeed;
import com.storeledger.common.DataSeedRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 기본 카테고리를 DB마다 딱 한 번 넣는다. 넣은 사실을 data_seeds 에 기록하므로,
 * 사용자가 기본 카테고리를 지우거나 전부 지워도 재시작할 때 다시 생기지 않는다.
 */
@Component
public class DefaultCategorySeeder implements ApplicationRunner {

    public static final List<String> NAMES = List.of(
            "Kitchen", "가구", "Living", "Bathroom", "Bedding", "Pet",
            "케이스", "패션", "차량용품 및 기타용품", "캐릭터 상품", "Christmas");

    private static final String SEED_ID = "default-categories";

    private final CategoryRepository categories;
    private final DataSeedRepository seeds;

    public DefaultCategorySeeder(CategoryRepository categories, DataSeedRepository seeds) {
        this.categories = categories;
        this.seeds = seeds;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seeds.existsById(SEED_ID)) {
            return;
        }
        for (String name : NAMES) {
            if (!categories.existsByNameIgnoreCase(name)) { // 이미 같은 이름이 있으면 건너뛴다 (복원한 DB 등)
                categories.save(new Category(name));
            }
        }
        seeds.save(new DataSeed(SEED_ID));
    }
}
