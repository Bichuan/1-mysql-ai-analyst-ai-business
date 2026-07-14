package com.aianalyst.vo;

import java.util.List;

/** Standard pagination response shared by list-style APIs. */
public record PageResultVO<T>(
        List<T> records,
        long total,
        long page,
        long size,
        long pages) {
}
