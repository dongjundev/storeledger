package com.storeledger.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query("""
            select s from Sale s join fetch s.product
            where s.saleDate between :fromDate and :toDate
            order by s.saleDate desc, s.id desc
            """)
    List<Sale> findBetween(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    boolean existsByProductId(Long productId);
}
