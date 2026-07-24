package com.jixiaotong.performance.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <T> PageResult<T> from(Page<T> page) {
        return PageResult.<T>builder()
                .records(page.getRecords())
                .total(page.getTotal())
                .current(page.getCurrent())
                .size(page.getSize())
                .build();
    }

    public static <T> PageResult<T> of(List<T> all, long current, long size) {
        if (all == null) {
            all = Collections.emptyList();
        }
        long pageNo = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : size;
        long total = all.size();
        int from = (int) Math.min((pageNo - 1) * pageSize, total);
        int to = (int) Math.min(from + pageSize, total);
        List<T> records = from >= to ? Collections.emptyList() : all.subList(from, to);
        return PageResult.<T>builder()
                .records(records)
                .total(total)
                .current(pageNo)
                .size(pageSize)
                .build();
    }
}
