package com.shiliuai.dto;

/**
 * 视流 AI · Codex 直接调用入参。
 * - prompt：要交给 codex 的指令
 * - workingDir：可选，覆盖默认 working dir（不传则用 application.yml 默认）
 * - sandbox：可选，read-only / workspace-write 等，默认 read-only 保证安全
 * - source：来源标记（android / feishu / api）
 */
public class CodexRunRequest {
    public String prompt;
    public String workingDir;
    public String sandbox;
    public String source;
}
