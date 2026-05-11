package com.example.ecommerce.productservice.repository;

import com.example.ecommerce.productservice.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlugAndActiveTrue(String slug);

    Optional<Product> findBySlugAndActiveTrueAndCategoryActiveTrue(String slug);

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Product> findAllByActiveTrue();

    List<Product> findByCategorySlugAndActiveTrueAndCategoryActiveTrue(String categorySlug);

    @Query("""
        select p
        from Product p
        join p.category c
        where p.active = true
            and c.active = true
            and (:categorySlug is null or c.slug = :categorySlug)
            and (
                :keyword is null
                or :keyword = ''
                or lower(p.name) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(p.description, '')) like lower(concat('%', :keyword, '%'))
            )
        """)
    Page<Product> searchActiveProducts(
        @Param("keyword") String keyword,
        @Param("categorySlug") String categorySlug,
        Pageable pageable
    );
}
