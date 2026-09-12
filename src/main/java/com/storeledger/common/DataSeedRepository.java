package com.storeledger.common;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSeedRepository extends JpaRepository<DataSeed, String> {
}
