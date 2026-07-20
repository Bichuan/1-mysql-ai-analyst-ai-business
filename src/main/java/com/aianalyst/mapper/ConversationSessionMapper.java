package com.aianalyst.mapper;

import com.aianalyst.entity.ConversationSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Mapper for durable conversation sessions in the system database. */
@Mapper
public interface ConversationSessionMapper extends BaseMapper<ConversationSession> {

    @Select("SELECT * FROM conversation_session WHERE conversation_id = #{conversationId} LIMIT 1")
    ConversationSession selectByConversationId(@Param("conversationId") String conversationId);

    @Select("SELECT * FROM conversation_session "
            + "WHERE conversation_id = #{conversationId} AND user_id = #{userId} LIMIT 1 FOR UPDATE")
    ConversationSession selectOwnedForUpdate(@Param("userId") Long userId,
                                             @Param("conversationId") String conversationId);

    @Update("""
            UPDATE conversation_session
            SET rolling_summary = #{rollingSummary},
                summary_until_turn = #{summaryUntilTurn},
                structured_state = #{structuredState},
                estimated_tokens = #{estimatedTokens},
                version = version + 1,
                last_active_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
              AND version = #{expectedVersion}
            """)
    int compareAndSetContextState(@Param("userId") Long userId,
                                  @Param("conversationId") String conversationId,
                                  @Param("expectedVersion") long expectedVersion,
                                  @Param("rollingSummary") String rollingSummary,
                                  @Param("summaryUntilTurn") long summaryUntilTurn,
                                  @Param("structuredState") String structuredState,
                                  @Param("estimatedTokens") int estimatedTokens);
}
