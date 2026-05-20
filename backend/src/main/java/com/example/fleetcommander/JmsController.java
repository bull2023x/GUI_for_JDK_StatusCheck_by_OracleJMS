package com.example.fleetcommander;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class JmsController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/api/managed-instances")
    public List<Map<String, Object>> managedInstances() throws Exception {
        JsonNode items = loadManagedInstanceItems();
        List<Map<String, Object>> result = new ArrayList<>();

        for (JsonNode item : items) {
            String hostname = text(item, "hostname");
            String managedInstanceId = text(item, "managed-instance-id");
            String managedInstanceType = text(item, "managed-instance-type");

            String javaVersion = text(item.path("agent"), "java-version");
            String securityStatus = text(item.path("agent"), "java-security-status");
            String agentDisplayName = text(item.path("agent"), "display-name");
            String agentType = text(item.path("agent"), "type");

            String osName = text(item.path("operating-system"), "distribution");
            String osFamily = text(item.path("operating-system"), "family");
            String osArchitecture = text(item.path("operating-system"), "architecture");
            String osVersion = text(item.path("operating-system"), "version");

            int appCount = item.path("approximate-application-count").asInt(0);
            int installationCount = item.path("approximate-installation-count").asInt(0);
            int jreCount = item.path("approximate-jre-count").asInt(0);

            String timeFirstSeen = text(item, "time-first-seen");
            String timeLastSeen = text(item, "time-last-seen");

            int riskScore = calculateRiskScore(javaVersion, securityStatus, appCount, installationCount, jreCount);
            String riskLevel = riskLevel(riskScore, securityStatus);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hostname", hostname);
            row.put("managedInstanceId", managedInstanceId);
            row.put("managedInstanceType", managedInstanceType);
            row.put("agentDisplayName", agentDisplayName);
            row.put("agentType", agentType);
            row.put("javaVersion", javaVersion);
            row.put("javaSecurityStatus", securityStatus);
            row.put("osName", osName);
            row.put("osFamily", osFamily);
            row.put("osArchitecture", osArchitecture);
            row.put("osVersion", osVersion);
            row.put("applicationCount", appCount);
            row.put("installationCount", installationCount);
            row.put("jreCount", jreCount);
            row.put("timeFirstSeen", timeFirstSeen);
            row.put("timeLastSeen", timeLastSeen);
            row.put("riskScore", riskScore);
            row.put("riskLevel", riskLevel);
            row.put("recommendation", recommendation(hostname, javaVersion, securityStatus, riskScore));

            result.add(row);
        }

        return result;
    }

    @GetMapping("/api/risk-summary")
    public Map<String, Object> riskSummary() throws Exception {
        List<Map<String, Object>> instances = managedInstances();

        int total = instances.size();
        int critical = 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        int totalRisk = 0;

        String topRiskHost = null;
        int topRiskScore = -1;

        for (Map<String, Object> instance : instances) {
            int score = (int) instance.get("riskScore");
            String level = (String) instance.get("riskLevel");

            totalRisk += score;

            if ("CRITICAL".equals(level)) {
                critical++;
            } else if ("HIGH".equals(level)) {
                high++;
            } else if ("MEDIUM".equals(level)) {
                medium++;
            } else {
                low++;
            }

            if (score > topRiskScore) {
                topRiskScore = score;
                topRiskHost = (String) instance.get("hostname");
            }
        }

        int overallRiskScore = total == 0 ? 0 : totalRisk / total;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fleetName", "R4_Advanced_JMS");
        summary.put("totalManagedInstances", total);
        summary.put("overallRiskScore", overallRiskScore);
        summary.put("criticalCount", critical);
        summary.put("highCount", high);
        summary.put("mediumCount", medium);
        summary.put("lowCount", low);
        summary.put("topRiskHost", topRiskHost == null ? "" : topRiskHost);

        return summary;
    }

    private JsonNode loadManagedInstanceItems() throws Exception {
        ClassPathResource resource = new ClassPathResource("jms-data/managed-instances.json");
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            return root.path("data").path("items");
        }
    }

    private int calculateRiskScore(String javaVersion, String securityStatus, int appCount, int installationCount, int jreCount) {
        int score = 0;

        if (javaVersion != null && javaVersion.startsWith("1.8")) {
            score += 30;
        }

        if ("UPDATE_REQUIRED".equalsIgnoreCase(securityStatus)) {
            score += 35;
        }

        if (appCount >= 5) {
            score += 15;
        } else if (appCount >= 3) {
            score += 10;
        }

        if (installationCount >= 4) {
            score += 10;
        } else if (installationCount >= 2) {
            score += 5;
        }

        if (jreCount >= 3) {
            score += 10;
        } else if (jreCount >= 1) {
            score += 5;
        }

        return Math.min(score, 100);
    }

    private String riskLevel(int score, String securityStatus) {
        if ("VULNERABLE".equalsIgnoreCase(securityStatus)
                || "UNSUPPORTED".equalsIgnoreCase(securityStatus)
                || "KNOWN_SECURITY_ISSUES".equalsIgnoreCase(securityStatus)) {
            return "CRITICAL";
        }

        if (score >= 70) {
            return "HIGH";
        }
        if (score >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String recommendation(String hostname, String javaVersion, String securityStatus, int riskScore) {
        if (riskScore >= 90) {
            return hostname + " is a critical Java runtime risk. Prioritize security update or migration to Java 17/21.";
        }
        if (riskScore >= 70) {
            return hostname + " requires attention. Review Java version " + javaVersion + " and apply required updates.";
        }
        if ("UPDATE_REQUIRED".equalsIgnoreCase(securityStatus)) {
            return hostname + " has Java updates required. Plan remediation.";
        }
        return hostname + " is currently low risk.";
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }
}
