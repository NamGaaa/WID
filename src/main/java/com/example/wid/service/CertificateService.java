package com.example.wid.service;

import com.example.wid.controller.exception.InvalidCertificateException;
import com.example.wid.controller.exception.InvalidKeyPairException;
import com.example.wid.controller.exception.UserNotFoundException;
import com.example.wid.dto.base.BaseCertificateDTO;
import com.example.wid.entity.*;
import com.example.wid.entity.base.BaseCertificateEntity;
import com.example.wid.entity.enums.CertificateType;
import com.example.wid.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import java.io.File;
import java.io.IOException;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class CertificateService {
    private final MemberRepository memberRepository;
    private final ClassCertificateRepository classCertificateRepository;
    private final CompetitionCertificateRepository competitionCertificateRepository;
    private final CertificateInfoRepository certificateInfoRepository;
    private final EncryptInfoRepository encryptInfoRepository;


    @Autowired
    public CertificateService(MemberRepository memberRepository, ClassCertificateRepository classCertificateRepository, CompetitionCertificateRepository competitionCertificateRepository, CertificateInfoRepository certificateInfoRepository, EncryptInfoRepository encryptInfoRepository) {
        this.memberRepository = memberRepository;
        this.classCertificateRepository = classCertificateRepository;
        this.competitionCertificateRepository = competitionCertificateRepository;
        this.certificateInfoRepository = certificateInfoRepository;
        this.encryptInfoRepository = encryptInfoRepository;
    }

    // 증명서 생성
    public void createCertificate(BaseCertificateDTO certificateDTO, Authentication authentication, CertificateType certificateType) throws IOException {
        // 사용자 인증서 매핑정보 저장
        MemberEntity issuerEntity = null;
        MemberEntity userEntity = null;

        // 이슈어 정보 확인
        if (memberRepository.findByEmail(certificateDTO.getIssuerEmail()).isPresent()) {
            issuerEntity = memberRepository.findByEmail(certificateDTO.getIssuerEmail()).get();
        } else throw new UserNotFoundException("인증서 발급자 정보가 올바르지 않습니다.");

        // 사용자 정보 확인
        if (memberRepository.findByUsername(authentication.getName()).isPresent()) {
            userEntity = memberRepository.findByUsername(authentication.getName()).get();
        } else throw new UserNotFoundException("사용자가 존재하지 않습니다.");


        CertificateInfoEntity certificateInfoEntity = CertificateInfoEntity.builder()
                .issuer(issuerEntity)
                .user(userEntity)
                .certificateType(certificateType)
                .build();
        CertificateInfoEntity savedCertificateInfo = certificateInfoRepository.save(certificateInfoEntity);

        MultipartFile file = certificateDTO.getFile();
        // 파일 저장 경로 설정
        // 저장경로 상대경로로 수정 요망
        String savePath = "C:/Users/SAMSUNG/Desktop/wid/src/main/resources/static/" + certificateDTO.getStoredFilename();
        file.transferTo(new File(savePath));

        BaseCertificateEntity baseCertificateEntity = certificateDTO.toCertificateEntity();
        if(Boolean.FALSE.equals(saveCertificate(baseCertificateEntity, savedCertificateInfo))) throw new InvalidCertificateException("인증서 정보가 올바르지 않습니다.");
    }
    // issuer가 서명
    public void signCertificateIssuer(Long certificateId, String encodedPrivateKey, Authentication authentication){
        try{
            MemberEntity issuer = null;
            if (memberRepository.findByUsername(authentication.getName()).isPresent()) {
                issuer = memberRepository.findByUsername(authentication.getName()).get();
            } else throw new UserNotFoundException("사용자가 존재하지 않습니다.");

            // 인코딩된 개인키와 공개키를 PrivateKey, PublicKey 객체로 변환
            PrivateKey privateKey = KeyFactory
                    .getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64
                            .getDecoder()
                            .decode(encodedPrivateKey.getBytes())
                    ));
            PublicKey publicKey = KeyFactory
                    .getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64
                            .getDecoder()
                            .decode(issuer.getPublicKey().getBytes())
                    ));

            if(!verifyKey(publicKey, privateKey)) throw new InvalidKeyPairException();

            // 인증서 정보 가져오기
            CertificateInfoEntity certificateInfo = null;
            if(certificateInfoRepository.findById(certificateId).isPresent()){
                certificateInfo = certificateInfoRepository.findById(certificateId).get();
            } else throw new InvalidCertificateException("인증서 정보가 올바르지 않습니다.");
            if(encryptInfoRepository.findByCertificateInfoId(certificateId).isPresent()){
                throw new InvalidCertificateException("이미 서명된 인증서입니다.");
            }

            // 인증서 정보에 저장된 하위 증명서 가져오기
            CertificateType certificateType = certificateInfo.getCertificateType();
            BaseCertificateEntity baseCertificateEntity = getCertificate(certificateId, certificateType);
            if(baseCertificateEntity == null) throw new InvalidCertificateException("인증서 정보가 올바르지 않습니다.");

            // 서명 정보 생성
            String serializedClassCertificate = baseCertificateEntity.serializeCertificateForSignature();
            byte[] encryptData = encrypt(serializedClassCertificate, privateKey);

            CertificateInfoEntity savedCertificateInfo = certificateInfoRepository.save(certificateInfo);
            EncryptInfoEntity encryptInfoEntity = EncryptInfoEntity.builder()
                    .issuerEncrypt(serializedClassCertificate)
                    .issuerPublicKey(issuer.getPublicKey())
                    .issuerEncrypt(Base64.getEncoder().encodeToString(encryptData))
                    .certificateInfo(savedCertificateInfo)
                    .build();

            // 서명 정보 저장
            encryptInfoRepository.save(encryptInfoEntity);
        } catch (Exception e) {
            throw new IllegalArgumentException("서명에 실패하였습니다.");
        }
    }
    
    // 사용자가 서명
    public void signCertificateUser(Long certificateId, Authentication authentication){
        try{
            MemberEntity user = null;
            if (memberRepository.findByUsername(authentication.getName()).isPresent()) {
                user = memberRepository.findByUsername(authentication.getName()).get();
            } else throw new UserNotFoundException("사용자가 존재하지 않습니다.");
            PublicKey publicKey = KeyFactory
                    .getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64
                            .getDecoder()
                            .decode(user.getPublicKey().getBytes())
                    ));

            CertificateInfoEntity certificateInfo = null;
            if(certificateInfoRepository.findById(certificateId).isPresent()){
                certificateInfo = certificateInfoRepository.findById(certificateId).get();
            } else throw new InvalidCertificateException("인증서 정보가 올바르지 않습니다.");

            CertificateType certificateType = certificateInfo.getCertificateType();
            BaseCertificateEntity baseCertificateEntity = getCertificate(certificateId, certificateType);
            if(baseCertificateEntity == null) throw new InvalidCertificateException("인증서 정보가 올바르지 않습니다.");
            if(certificateInfo.getUser() != user) throw new InvalidCertificateException("인증서에 대한 권한이 없습니다.");

            // 올바른 서명정보인지 확인
            EncryptInfoEntity encryptInfo = null;
            if(encryptInfoRepository.findByCertificateInfoId(certificateId).isPresent()){
                encryptInfo = encryptInfoRepository.findByCertificateInfoId(certificateId).get();
            } else throw new InvalidCertificateException("발급자 서명 완료되지 않았습니다.");

            String issuerEncrypt = encryptInfo.getIssuerEncrypt();
            byte[] encryptData = encrypt(issuerEncrypt, publicKey);

            String encodedUserEncrypt = Base64.getEncoder().encodeToString(encryptData);

            // 블록체인 네트워크 연동 후 encryptInfo
            encryptInfo.setUserEncrypt(encodedUserEncrypt);
            encryptInfoRepository.save(encryptInfo);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("InvalidKeySpecException");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("NoSuchAlgorithmException");
        } catch (Exception e) {
            throw new RuntimeException("서명에 실패하였습니다.");
        }
    }

    // 클래스 내부 메소드
    // 테스트를 위해 public으로 선언 , 실제 서비스에서는 private로 변경
    public byte[] encrypt(String data, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data.getBytes());
    }

    public byte[] decrypt(byte[] encryptedData, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(encryptedData);
    }

    public boolean verifyKey(PublicKey publicKey, PrivateKey privateKey) {
        try {
            String testData = "Test data";

            // Encrypt with private key and decrypt with public key
            byte[] encryptedData = encrypt(testData, publicKey);
            byte[] decryptedData = decrypt(encryptedData, privateKey);

            // Compare original data with decrypted data
            return testData.equals(new String(decryptedData));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean saveCertificate(BaseCertificateEntity baseCertificateEntity, CertificateInfoEntity certificateInfoEntity) {
        if(baseCertificateEntity instanceof ClassCertificateEntity classCertificateEntity){
            classCertificateEntity = (ClassCertificateEntity) baseCertificateEntity;
            classCertificateEntity.setCertificateInfo(certificateInfoEntity);
            classCertificateRepository.save(classCertificateEntity);
            return true;
        } else if(baseCertificateEntity instanceof CompetitionCertificateEntity competitionCertificateEntity){
            competitionCertificateEntity = (CompetitionCertificateEntity) baseCertificateEntity;
            competitionCertificateEntity.setCertificateInfo(certificateInfoEntity);
            competitionCertificateRepository.save(competitionCertificateEntity);
            return true;
        }
        return false;
    }
    public BaseCertificateEntity getCertificate(Long certificateId, CertificateType certificateType) {
        if (certificateType == CertificateType.CLASS_CERTIFICATE && classCertificateRepository.findByCertificateInfo_Id(certificateId).isPresent()) {
            return classCertificateRepository.findByCertificateInfo_Id(certificateId).get();
        } else if (certificateType == CertificateType.COMPETITION_CERTIFICATE && competitionCertificateRepository.findByCertificateInfo_Id(certificateId).isPresent()) {
            return competitionCertificateRepository.findByCertificateInfo_Id(certificateId).get();
        } else return null;
    }
}

