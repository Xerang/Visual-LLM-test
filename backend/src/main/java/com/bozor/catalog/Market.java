package com.bozor.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 두샨베 시장(Mehrgon, Korvon 등). 지도·길찾기는 범위 밖. */
@Entity
@Table(name = "markets")
public class Market {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String city;

    protected Market() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
}
