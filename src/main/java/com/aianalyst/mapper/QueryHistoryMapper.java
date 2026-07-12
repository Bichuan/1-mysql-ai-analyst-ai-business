package com.aianalyst.mapper;

import com.aianalyst.entity.QueryHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** Mapper for query audit history in the system database. */
@Mapper
public interface QueryHistoryMapper extends BaseMapper<QueryHistory> {
}
