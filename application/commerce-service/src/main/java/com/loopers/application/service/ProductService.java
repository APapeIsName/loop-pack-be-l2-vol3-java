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
import com.loopers.domain.catalog.product.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
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

    @Transactional(readOnly = true)
    public ProductInfo getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        ProductExceptionMessage.Product.NOT_FOUND.message()));

        Brand brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        BrandExceptionMessage.Brand.NOT_FOUND.message()));

        return toProductInfo(product, brand);
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

    @Transactional
    public void update(Long id, ProductUpdateCommand command) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        ProductExceptionMessage.Product.NOT_FOUND.message()));

        product.update(
                command.name(),
                command.description(),
                Money.of(command.price()),
                Stock.of(command.stock())
        );
    }

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
                .map(product -> toProductInfo(product, brandMap.get(product.getBrandId())))
                .toList();
    }

    private ProductInfo toProductInfo(Product product, Brand brand) {
        return new ProductInfo(
                product.getId(),
                product.getName().getValue(),
                product.getDescription(),
                product.getPrice().getValue(),
                product.getStock().getValue(),
                product.getLikesCount(),
                brand != null ? brand.getName().getValue() : null
        );
    }
}
