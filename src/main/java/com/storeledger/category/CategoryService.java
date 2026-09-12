package com.storeledger.category;

import com.storeledger.product.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categories.findAllWithProductCount();
    }

    public CategoryResponse create(CategoryRequest request) {
        ensureUnique(request.name(), null);
        Category category = categories.save(new Category(request.name()));
        return new CategoryResponse(category.getId(), category.getName(), 0);
    }

    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = get(id);
        ensureUnique(request.name(), id);
        category.rename(request.name());
        return new CategoryResponse(category.getId(), category.getName(), products.countByCategory(category));
    }

    /** 카테고리를 지우면 그 카테고리의 상품은 미분류(카테고리 없음)가 된다. */
    public void delete(Long id) {
        Category category = get(id);
        products.clearCategory(category);
        categories.delete(category);
    }

    public Category get(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다: " + id));
    }

    /** 대소문자만 다른 이름(pet / Pet)도 같은 카테고리로 본다. */
    private void ensureUnique(String name, Long id) {
        boolean taken = id == null
                ? categories.existsByNameIgnoreCase(name)
                : categories.existsByNameIgnoreCaseAndIdNot(name, id);
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 있는 카테고리입니다: " + name);
        }
    }
}
