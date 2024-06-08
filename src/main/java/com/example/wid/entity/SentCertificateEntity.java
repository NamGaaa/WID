package com.example.wid.entity;

import com.example.wid.entity.base.BaseEntity;
import com.example.wid.entity.enums.CertificateType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 인증서를 verifier에게 전송한 이력을 저장하는 entity
@Entity(name = "sent_certificate_info")
@Getter
@Setter
@NoArgsConstructor
public class SentCertificateEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id")
    private CertificateInfoEntity certificate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verifier_id")
    private MemberEntity verifier;

    @Builder
    public SentCertificateEntity(CertificateInfoEntity certificate, MemberEntity verifier) {
        this.certificate = certificate;
        this.verifier = verifier;
    }
}
