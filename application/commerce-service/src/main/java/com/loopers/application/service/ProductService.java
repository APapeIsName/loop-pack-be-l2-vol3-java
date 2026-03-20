package com.loopers.application.service;

import com.loopers.application.service.dto.ProductCreateCommand;
import com.loopers.application.service.dto.ProductInfo;
import com.loopers.application.service.dto.ProductUpdateCommand;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandExceptionMessage;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductExceptionMessage;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductSortType;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @CacheEvict(cacheNames = "products", allEntries = true)
    @Transactional
    public void create(ProductCreateCommand command) {
        Brand brand = brandRepository.findById(command.brandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        BrandExceptionMessage.Brand.NOT_FOUND.message()));

        if (brand.isDeleted()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    BrandExceptionMessage.Brand.ALREADY_DELETED.message());
        }

        Product product = Product.register(
                command.name(),
                command.description(),
                Money.of(command.price()),
                Stock.of(command.stock()),
                command.brandId()
        );
        productRepository.save(product);
    }

    @Cacheable(cacheNames = "product", key = "#id")
    @Transactional(readOnly = true)
    public ProductInfo getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        ProductExceptionMessage.Product.NOT_FOUND.message()));

        Brand brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        BrandExceptionMessage.Brand.NOT_FOUND.message()));

        return ProductInfo.from(product, brand);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAll() {
        List<Product> products = productRepository.findAll();
        return toProductInfos(products);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getActiveProducts(ProductSortType sortType) {
        List<Product> products = productRepository.findAllActive(sortType);
        return toProductInfos(products);
    }

    @Cacheable(cacheNames = "products",
            key = "#sortType + ':' + (#brandId ?: 'all') + ':page=' + #page + ':size=' + #size",
            condition = "#page <= 3")
    @Transactional(readOnly = true)
    public List<ProductInfo> getActiveProducts(ProductSortType sortType, Long brandId, int page, int size) {
        List<Product> products = productRepository.findAllActive(sortType, brandId, page, size);
        return toProductInfos(products);
    }

    @Caching(
            put = @CachePut(cacheNames = "product", key = "#id"),
            evict = @CacheEvict(cacheNames = "products", allEntries = true)
    )
    @Transactional
    public ProductInfo update(Long id, ProductUpdateCommand command) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        ProductExceptionMessage.Product.NOT_FOUND.message()));

        product.update(
                command.name(),
                command.description(),
                Money.of(command.price()),
                Stock.of(command.stock())
        );

        Brand brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        BrandExceptionMessage.Brand.NOT_FOUND.message()));

        return ProductInfo.from(product, brand);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "product", key = "#id"),
            @CacheEvict(cacheNames = "products", allEntries = true)
    })
    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        ProductExceptionMessage.Product.NOT_FOUND.message()));

        product.delete();
    }

    private List<ProductInfo> toProductInfos(List<Product> products) {
        List<Long> brandIds = products.stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();

        Map<Long, Brand> brandMap = brandRepository.findAllByIdIn(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        return products.stream()
                .map(product -> ProductInfo.from(product, brandMap.get(product.getBrandId())))
                .toList();
    }
}
