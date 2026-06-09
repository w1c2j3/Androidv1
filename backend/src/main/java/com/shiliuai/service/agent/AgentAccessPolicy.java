package com.shiliuai.service.agent;

import com.shiliuai.config.ShiliuProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;

@Service
public class AgentAccessPolicy {
    private final ShiliuProperties properties;

    public AgentAccessPolicy(ShiliuProperties properties) {
        this.properties = properties;
    }

    public String resolveProjectPath(String requestedPath) {
        List<Path> roots = allowedRoots();
        if (roots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "未配置 Agent 可访问项目路径");
        }
        if (!StringUtils.hasText(requestedPath)) {
            return roots.get(0).toString();
        }

        Path rawPath = Path.of(requestedPath.trim());
        if (!rawPath.isAbsolute()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目路径必须是绝对路径");
        }
        Path normalizedPath = rawPath.normalize();
        boolean allowed = roots.stream().anyMatch(root -> normalizedPath.equals(root) || normalizedPath.startsWith(root));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "项目路径不在允许访问范围内");
        }
        return normalizedPath.toString();
    }

    private List<Path> allowedRoots() {
        List<String> configuredRoots = properties.getAgent().getAllowedProjectRoots();
        if (configuredRoots == null) {
            return List.of();
        }
        return configuredRoots.stream()
                .filter(StringUtils::hasText)
                .map(value -> Path.of(value.trim()).toAbsolutePath().normalize())
                .distinct()
                .toList();
    }
}
