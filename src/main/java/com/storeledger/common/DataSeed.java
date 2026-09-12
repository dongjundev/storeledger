package com.storeledger.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 이미 넣은 초기 데이터 기록. 같은 초기 데이터를 두 번 넣지 않기 위해 쓴다. */
@Entity
@Table(name = "data_seeds")
public class DataSeed {

    @Id
    @Column(length = 100)
    private String id;

    protected DataSeed() {
    }

    public DataSeed(String id) {
        this.id = id;
    }
}
