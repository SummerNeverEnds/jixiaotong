package com.jixiaotong.performance.dto;

import lombok.Data;

@Data
public class ChangePasswordDTO {
    private Long userId;
    private String oldPassword;
    private String newPassword;
}
