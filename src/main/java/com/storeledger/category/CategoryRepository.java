package com.storeledger.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("""
            select new com.storeledger.category.CategoryResponse(c.id, c.name, count(p))
            from Category c left join Product p on p.category = c
            group by c.id, c.name
            order by c.id
            """)
    List<CategoryResponse> findAllWithProductCount();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
