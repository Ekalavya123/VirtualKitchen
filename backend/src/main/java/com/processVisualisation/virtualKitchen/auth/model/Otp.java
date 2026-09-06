package com.processVisualisation.virtualKitchen.auth.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "otps")
public class Otp {

    @Id
    private String id;

    @Indexed
    private String email;

    private OtpPurpose purpose;

    private String codeHash;

    private LocalDateTime expiresAt;

    private boolean verified = false;

    private boolean consumed = false;

    private int attempts = 0;

    @CreatedDate
    private LocalDateTime createdAt;
}
