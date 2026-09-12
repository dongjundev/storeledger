package com.storeledger.product;

import com.storeledger.category.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByOrderByNameAsc();

    long countByCategory(Category category);

    /** 카테고리를 지우기 전에 그 카테고리의 상품을 미분류로 돌린다. 이미 읽어 둔 상품이 옛 카테고리를 들고 있지 않게 비운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.category = null where p.category = :category")
    void clearCategory(@Param("category") Category category);
}
