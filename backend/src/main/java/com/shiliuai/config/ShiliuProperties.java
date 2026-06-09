package com.shiliuai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "shiliu")
public class ShiliuProperties {
    private String publicBaseUrl = "http://localhost:8080";
    private String adminToken = "dev-admin-token";
    private String fileStorageDir = "./data/files";
    private final Agent agent = new Agent();
    private final Ocr ocr = new Ocr();
    private final Llm llm = new Llm();
    private final Feishu feishu = new Feishu();

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }

    public String getFileStorageDir() {
        return fileStorageDir;
    }

    public void setFileStorageDir(String fileStorageDir) {
        this.fileStorageDir = fileStorageDir;
    }

    public Ocr getOcr() {
        return ocr;
    }

    public Agent getAgent() {
        return agent;
    }

    public static class Agent {
        private List<String> allowedProjectRoots = new ArrayList<>(List.of("/home/chase/GitHub/shiliu-ai-v1"));

        public List<String> getAllowedProjectRoots() {
            return allowedProjectRoots;
        }

        public void setAllowedProjectRoots(List<String> allowedProjectRoots) {
            this.allowedProjectRoots = allowedProjectRoots;
        }
    }

    public Llm getLlm() {
        return llm;
    }

    public Feishu getFeishu() {
        return feishu;
    }

    public static class Ocr {
        private String httpEndpoint = "http://localhost:9000/ocr";

        public String getHttpEndpoint() {
            return httpEndpoint;
        }

        public void setHttpEndpoint(String httpEndpoint) {
            this.httpEndpoint = httpEndpoint;
        }
    }

    public static class Llm {
        private boolean enabled = true;
        private String apiBaseUrl = "";
        private String apiKey = "";
        private String model = "gpt-4o-mini";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Feishu {
        private String apiBaseUrl = "https://open.feishu.cn/open-apis";

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }
    }
}
