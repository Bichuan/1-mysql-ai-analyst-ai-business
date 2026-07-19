package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.service.QueryPromptInjectionValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt 注入前置校验器。这里只做低成本的确定性拦截，后续 Prompt 约束、SQL AST 审核和
 * MySQL 只读账号仍然保留，不能因为增加了前置规则就移除后续防线。
 */
@Component
public class RegexQueryPromptInjectionValidator implements QueryPromptInjectionValidator {

    private static final String PROMPT_OR_INSTRUCTION =
            "(?:系统\\s*(?:prompt|提示词|提示语|指令|消息)|隐藏\\s*(?:prompt|提示词|指令)|system\\s+prompt|hidden\\s+(?:prompt|instructions?)|developer\\s+(?:prompt|instructions?))";
    private static final String DISCLOSURE_ACTION =
            "(?:输出|显示|展示|打印|返回|告诉|给我看|泄露|复述|复制|show|reveal|print|display|return|repeat|leak|expose|tell\\s+me)";

    private static final List<Pattern> ATTACK_PATTERNS = List.of(
            // 索取或泄露系统 Prompt，动作和目标的先后顺序都要覆盖。
            Pattern.compile("(?i)" + DISCLOSURE_ACTION + ".{0,60}" + PROMPT_OR_INSTRUCTION),
            Pattern.compile("(?i)" + PROMPT_OR_INSTRUCTION
                    + ".{0,40}(?:是什么|内容|给我看|输出|显示|展示|告诉|show|reveal|display|what\\s+is)"),
            // 典型 jailbreak：要求忽略此前规则、系统指令或安全限制。
            Pattern.compile("(?i)(?:忽略|无视|绕过|跳过|取消|忘记|ignore|bypass|forget).{0,50}"
                    + "(?:之前|以上|所有|安全|系统|previous|system|security).{0,30}"
                    + "(?:规则|指令|限制|提示|instructions?|rules?|prompt)"),
            // 通过重新定义角色让模型不再充当受控 SQL 引擎。
            Pattern.compile("(?i)(?:你现在不是|从现在起你不是|现在开始你不是|you\\s+are\\s+not|stop\\s+being)"
                    + ".{0,40}(?:sql|查询).{0,20}(?:引擎|助手|engine|assistant)"),
            Pattern.compile("(?i)(?:unrestricted|jailbroken|无限制|无约束|不受限制|越狱).{0,15}"
                    + "(?:ai|assistant|model|人工智能|助手|模型)"),
            Pattern.compile("(?i)(?:ai|assistant|model|人工智能|助手|模型).{0,15}"
                    + "(?:unrestricted|jailbroken|无限制|无约束|不受限制|越狱)"),
            Pattern.compile("(?i)(?:jailbreak|developer\\s+mode|越狱模式|开发者模式|dan\\s+mode)"),
            // 要求停止输出 SQL 并改成其他内容，本质是在劫持固定输出协议。
            Pattern.compile("(?i)(?:不要|禁止|停止|别|do\\s+not|don't|stop).{0,20}"
                    + "(?:输出|返回|生成|output|return|generate).{0,15}sql.{0,60}"
                    + "(?:输出|返回|改为|而是|instead|output|return|show)"),
            // 直接要求模型编写危险 DDL/DML，即使命令前有‘帮我生成’等包装也应前置拒绝。
            Pattern.compile("(?i)^\\s*(?:(?:请|帮我|给我|我要|需要|please)\\s*)*"
                    + "(?:生成|编写|构造|输出|返回|写|generate|write|provide).{0,40}"
                    + "(?:\\bdrop\\b|\\bdelete\\b|\\bupdate\\b|\\binsert\\b|\\btruncate\\b|\\balter\\b|"
                    + "删除语句|更新语句|插入语句|建表语句|删表语句)")
    );

    @Override
    public void validate(String question) {
        if (question == null) {
            return;
        }
        boolean attackDetected = ATTACK_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(question).find());
        if (attackDetected) {
            throw new BusinessException(ResultCode.PROMPT_INJECTION_DETECTED);
        }
    }
}
