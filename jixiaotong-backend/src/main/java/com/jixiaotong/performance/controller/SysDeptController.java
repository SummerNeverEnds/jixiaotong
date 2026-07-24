package com.jixiaotong.performance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jixiaotong.performance.common.Result;
import com.jixiaotong.performance.entity.SysDept;
import com.jixiaotong.performance.mapper.SysDeptMapper;
import com.jixiaotong.performance.security.AuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptMapper sysDeptMapper;

    @GetMapping("/list")
    public Result<List<SysDept>> list() {
        AuthSupport.requireRole("ADMIN", "MANAGER");
        return Result.success(sysDeptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getId)));
    }
}
