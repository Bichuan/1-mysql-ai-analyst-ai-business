package com.aianalyst.mapper;

import com.aianalyst.entity.ConversationMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Mapper for durable conversation messages in the system database. */
@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {

    @Update("""
            UPDATE conversation_message
            SET query_history_id = #{queryHistoryId}
            WHERE id = #{messageId} AND query_history_id IS NULL
            """)
    int linkQueryHistory(@Param("messageId") Long messageId,
                         @Param("queryHistoryId") Long queryHistoryId);
}
