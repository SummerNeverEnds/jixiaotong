package com.jixiaotong.performance.mapper;

import com.jixiaotong.performance.dto.DashboardStatDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DashboardMapper {
    
    List<DashboardStatDTO> getDashboardStats(@Param("cycleName") String cycleName);
}
