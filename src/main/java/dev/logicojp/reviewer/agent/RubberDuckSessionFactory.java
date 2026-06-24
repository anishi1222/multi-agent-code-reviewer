package dev.logicojp.reviewer.agent;

import com.github.copilot.rpc.McpServerConfig;

import java.util.Map;

@FunctionalInterface
interface RubberDuckSessionFactory {

    RubberDuckSession create(String model,
                             String systemPrompt,
                             Map<String, McpServerConfig> mcpServers,
                             String sessionTag) throws Exception;
}
