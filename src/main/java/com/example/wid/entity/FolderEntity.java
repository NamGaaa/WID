package com.example.wid.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity(name = "folder")
@Getter
@Setter
@NoArgsConstructor
public class FolderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    MemberEntity user;
    private String folderName;
    @OneToMany(fetch = FetchType.LAZY)
    List<FolderCertificateEntity> folderCertificates;

    @Builder
    public FolderEntity(MemberEntity user, String folderName) {
        this.user = user;
        this.folderName = folderName;
    }
}