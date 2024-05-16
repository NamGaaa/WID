package com.example.wid.entity;

import jakarta.persistence.*;

@Entity(name = "belong")
public class BelongEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String belongName;
    @Enumerated(EnumType.STRING)
    private Belong belongType;
}