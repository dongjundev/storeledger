package com.storeledger.product;

import com.storeledger.category.Category;
import com.storeledger.category.CategoryService;
import com.storeledger.sale.SaleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class ProductService {

    private final ProductRepository products;
    private final SaleRepository sales;
    private final CategoryService categories;

    public ProductService(ProductRepository products, SaleRepository sales, CategoryService categories) {
        this.products = products;
        this.sales = sales;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return products.findAllByOrderByNameAsc().stream().map(ProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse find(Long id) {
        return ProductResponse.from(get(id));
    }

    public ProductResponse create(ProductRequest request) {
        Product product = new Product(request.name(), category(request), request.sellingPrice(), request.costPrice(),
                request.shippingCost(), request.buyerShippingFee(), request.otherCost(),
                request.orderFeeRate(), request.salesFeeRate());
        return ProductResponse.from(products.save(product));
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Product product = get(id);
        product.update(request.name(), category(request), request.sellingPrice(), request.costPrice(),
                request.shippingCost(), request.buyerShippingFee(), request.otherCost(),
                request.orderFeeRate(), request.salesFeeRate());
        return ProductResponse.from(product);
    }

    public void delete(Long id) {
        Product product = get(id);
        if (sales.existsByProductId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "판매 기록이 있는 상품은 삭제할 수 없습니다. 판매 기록을 먼저 삭제해 주세요.");
        }
        products.delete(product);
    }

    private Category category(ProductRequest request) {
        return request.categoryId() == null ? null : categories.get(request.categoryId());
    }

    public Product get(Long id) {
        return products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다: " + id));
    }
}
