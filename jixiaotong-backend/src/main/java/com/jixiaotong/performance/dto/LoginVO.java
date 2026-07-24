package com.jixiaotong.performance.dto;

import com.jixiaotong.performance.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    
    private Long expiresIn;

    private SysUser user;
}
