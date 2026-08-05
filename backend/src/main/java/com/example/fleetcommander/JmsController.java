package com.example.fleetcommander;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.jms.JavaManagementServiceClient;
import com.oracle.bmc.jms.model.Agent;
import com.oracle.bmc.jms.model.InstallationUsage;
import com.oracle.bmc.jms.model.ManagedInstanceUsage;
import com.oracle.bmc.jms.model.OperatingSystem;
import com.oracle.bmc.jms.requests.SummarizeManagedInstanceUsageRequest;
import com.oracle.bmc.jms.requests.SummarizeInstallationUsageRequest;
import com.oracle.bmc.jms.responses.SummarizeManagedInstanceUsageResponse;
import com.oracle.bmc.jms.responses.SummarizeInstallationUsageResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
public class JmsController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private volatile JsonNode uploadedManagedInstancesRoot;
    private volatile JsonNode uploadedFleetsRoot;
    private volatile Instant uploadedAt;
    private volatile String dataSource = "classpath";

    @GetMapping("/api/managed-instances")
    public List<Map<String, Object>> managedInstances() throws Exception {
        JsonNode items = loadManagedInstanceItems();
        List<Map<String, Object>> result = new ArrayList<>();

        for (JsonNode item : items) {
            String hostname = textAny(item, "hostname", "hostName");
            String managedInstanceId = textAny(item, "managed-instance-id", "managedInstanceId");
            String managedInstanceType = textAny(item, "managed-instance-type", "managedInstanceType");

            JsonNode agent = item.path("agent");
            String javaVersion = textAny(agent, "java-version", "javaVersion");
            String securityStatus = textAny(agent, "java-security-status", "javaSecurityStatus");
            String agentDisplayName = textAny(agent, "display-name", "displayName");
            String agentType = text(item.path("agent"), "type");

            JsonNode operatingSystem = firstPresent(item, "operating-system", "operatingSystem");
            String osName = text(operatingSystem, "distribution");
            String osFamily = text(operatingSystem, "family");
            String osArchitecture = text(operatingSystem, "architecture");
            String osVersion = text(operatingSystem, "version");

            int appCount = intAny(item, "approximate-application-count", "approximateApplicationCount");
            int installationCount = intAny(item, "approximate-installation-count", "approximateInstallationCount");
            int jreCount = intAny(item, "approximate-jre-count", "approximateJreCount");

            String timeFirstSeen = textAny(item, "time-first-seen", "timeFirstSeen");
            String timeLastSeen = textAny(item, "time-last-seen", "timeLastSeen");
            List<Map<String, Object>> javaInstallations = installationRows(item);

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
            row.put("javaInstallations", javaInstallations);
            row.put("timeFirstSeen", timeFirstSeen);
            row.put("timeLastSeen", timeLastSeen);
            row.put("riskScore", riskScore);
            row.put("riskLevel", riskLevel);
            row.put("recommendation", recommendation(hostname, javaVersion, securityStatus, riskScore));

            result.add(row);
        }

        return result;
    }

    @PostMapping(value = "/api/jms-data/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadJmsData(
            @RequestParam(value = "managedInstances", required = false) MultipartFile managedInstancesFile,
            @RequestParam(value = "fleets", required = false) MultipartFile fleetsFile
    ) throws Exception {
        if (isMissing(managedInstancesFile) && isMissing(fleetsFile)) {
            throw new ResponseStatusException(BAD_REQUEST, "Upload managed-instances.json, fleets.json, or both.");
        }

        JsonNode nextManagedInstancesRoot = uploadedManagedInstancesRoot;
        JsonNode nextFleetsRoot = uploadedFleetsRoot;

        if (!isMissing(managedInstancesFile)) {
            nextManagedInstancesRoot = readJson(managedInstancesFile, "managed-instances.json");
            validateItemsPayload(nextManagedInstancesRoot, "managed-instances.json");
        }

        if (!isMissing(fleetsFile)) {
            nextFleetsRoot = readJson(fleetsFile, "fleets.json");
            validateItemsPayload(nextFleetsRoot, "fleets.json");
        }

        uploadedManagedInstancesRoot = nextManagedInstancesRoot;
        uploadedFleetsRoot = nextFleetsRoot;
        uploadedAt = Instant.now();
        dataSource = "uploaded";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "JMS JSON data uploaded.");
        response.put("uploadedAt", uploadedAt.toString());
        response.put("managedInstanceCount", loadManagedInstanceItems().size());
        response.put("fleetName", loadFleetName());
        return response;
    }

    @GetMapping("/api/jms-data/status")
    public Map<String, Object> dataStatus() throws Exception {
        JsonNode items = loadManagedInstanceItems();

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("source", dataSource);
        status.put("uploadedAt", uploadedAt == null ? "" : uploadedAt.toString());
        status.put("managedInstanceCount", items.size());
        status.put("fleetName", loadFleetName());
        return status;
    }

    @PostMapping("/api/oci/sync")
    public Map<String, Object> syncFromOci(
            @RequestParam(value = "compartmentId", required = false) String compartmentId,
            @RequestParam(value = "fleetId", required = false) String fleetId,
            @RequestParam(value = "fleetName", required = false) String fleetName,
            @RequestParam(value = "profile", required = false) String profile,
            @RequestParam(value = "region", required = false) String region
    ) throws Exception {
        String resolvedCompartmentId = firstNonBlank(compartmentId, System.getenv("OCI_COMPARTMENT_ID"));
        String resolvedFleetId = firstNonBlank(fleetId, System.getenv("OCI_FLEET_ID"));
        String resolvedFleetName = firstNonBlank(fleetName, System.getenv("OCI_FLEET_NAME"));
        String resolvedProfile = firstNonBlank(profile, System.getenv("OCI_CLI_PROFILE"), System.getenv("OCI_PROFILE"), "DEFAULT");
        String resolvedRegion = firstNonBlank(region, System.getenv("OCI_REGION"));

        if (resolvedFleetId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "fleetId or OCI_FLEET_ID is required for OCI sync.");
        }

        ConfigFileAuthenticationDetailsProvider provider =
                new ConfigFileAuthenticationDetailsProvider(resolvedProfile);

        try (JavaManagementServiceClient client = JavaManagementServiceClient.builder().build(provider)) {
            if (!resolvedRegion.isBlank()) {
                client.setRegion(Region.fromRegionId(resolvedRegion));
            }

            ArrayNode allItems = objectMapper.createArrayNode();
            String page = null;
            do {
                SummarizeManagedInstanceUsageRequest request = SummarizeManagedInstanceUsageRequest.builder()
                        .fleetId(resolvedFleetId)
                        .limit(1000)
                        .page(page)
                        .build();
                SummarizeManagedInstanceUsageResponse response = client.summarizeManagedInstanceUsage(request);
                for (ManagedInstanceUsage item : response.getManagedInstanceUsageCollection().getItems()) {
                    List<InstallationUsage> installations = summarizeInstallations(
                            client, resolvedFleetId, item.getManagedInstanceId());
                    allItems.add(toDashboardJson(item, installations));
                }
                page = response.getOpcNextPage();
            } while (page != null && !page.isBlank());

            ObjectNode data = objectMapper.createObjectNode();
            data.set("items", allItems);
            ObjectNode root = objectMapper.createObjectNode();
            root.set("data", data);

            ObjectNode fleet = objectMapper.createObjectNode();
            fleet.put("id", resolvedFleetId);
            fleet.put("display-name", resolvedFleetName.isBlank() ? resolvedFleetId : resolvedFleetName);
            ArrayNode fleetItems = objectMapper.createArrayNode().add(fleet);
            ObjectNode fleetData = objectMapper.createObjectNode();
            fleetData.set("items", fleetItems);
            ObjectNode fleetRoot = objectMapper.createObjectNode();
            fleetRoot.set("data", fleetData);

            uploadedManagedInstancesRoot = root;
            uploadedFleetsRoot = fleetRoot;
            uploadedAt = Instant.now();
            dataSource = "oci-sdk";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "JMS data synced from OCI.");
            result.put("source", dataSource);
            result.put("uploadedAt", uploadedAt.toString());
            result.put("compartmentId", resolvedCompartmentId);
            result.put("fleetId", resolvedFleetId);
            result.put("fleetName", loadFleetName());
            result.put("managedInstanceCount", allItems.size());
            result.put("profile", resolvedProfile);
            result.put("region", resolvedRegion);
            return result;
        }
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
        summary.put("fleetName", loadFleetName());
        summary.put("totalManagedInstances", total);
        summary.put("overallRiskScore", overallRiskScore);
        summary.put("criticalCount", critical);
        summary.put("highCount", high);
        summary.put("mediumCount", medium);
        summary.put("lowCount", low);
        summary.put("topRiskHost", topRiskHost == null ? "" : topRiskHost);

        return summary;
    }

    @GetMapping("/api/ai-analysis")
    public Map<String, Object> aiAnalysis() throws Exception {
        List<Map<String, Object>> instances = managedInstances();
        Map<String, Object> summary = riskSummary();

        String fallbackAnalysis = buildRuleBasedFleetAnalysis(summary, instances);
        String ollamaModel = envOrDefault("OLLAMA_MODEL", "llama3.1");
        String ollamaBaseUrl = envOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("generatedAt", Instant.now().toString());
        response.put("provider", "rule-based");
        response.put("model", "");
        response.put("available", false);
        response.put("analysis", fallbackAnalysis);

        try {
            String ollamaAnalysis = requestOllamaAnalysis(ollamaBaseUrl, ollamaModel, summary, instances);
            if (!ollamaAnalysis.isBlank()) {
                response.put("provider", "ollama");
                response.put("model", ollamaModel);
                response.put("available", true);
                response.put("analysis", ollamaAnalysis);
            }
        } catch (Exception exception) {
            response.put("note", "Ollama is not available. Returned rule-based analysis.");
        }

        return response;
    }

    private JsonNode loadManagedInstanceItems() throws Exception {
        JsonNode root = uploadedManagedInstancesRoot != null
                ? uploadedManagedInstancesRoot
                : loadClasspathJson("jms-data/managed-instances.json");

        return root.path("data").path("items");
    }

    private String loadFleetName() throws Exception {
        JsonNode root = uploadedFleetsRoot;
        if (root == null) {
            root = loadClasspathJson("jms-data/fleets.json");
        }

        JsonNode firstFleet = root.path("data").path("items").path(0);
        String displayName = text(firstFleet, "display-name");
        if (!displayName.isBlank()) {
            return displayName;
        }

        String fleetName = text(firstFleet, "name");
        return fleetName.isBlank() ? "Uploaded JMS Fleet" : fleetName;
    }

    private JsonNode loadClasspathJson(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readTree(inputStream);
        } catch (FileNotFoundException exception) {
            return objectMapper.createObjectNode()
                    .set("data", objectMapper.createObjectNode()
                            .set("items", objectMapper.createArrayNode()));
        }
    }

    private JsonNode readJson(MultipartFile file, String expectedName) throws IOException {
        if (file.getOriginalFilename() != null && !file.getOriginalFilename().endsWith(".json")) {
            throw new ResponseStatusException(BAD_REQUEST, expectedName + " must be a JSON file.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            return objectMapper.readTree(inputStream);
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not parse " + expectedName + ".", exception);
        }
    }

    private void validateItemsPayload(JsonNode root, String fileName) {
        JsonNode items = root.path("data").path("items");
        if (!items.isArray()) {
            throw new ResponseStatusException(BAD_REQUEST, fileName + " must contain data.items as an array.");
        }
    }

    private boolean isMissing(MultipartFile file) {
        return file == null || file.isEmpty();
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

    private String requestOllamaAnalysis(
            String ollamaBaseUrl,
            String model,
            Map<String, Object> summary,
            List<Map<String, Object>> instances
    ) throws Exception {
        String prompt = buildOllamaPrompt(summary, instances);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);
        requestBody.put("options", Map.of(
                "temperature", 0.2,
                "num_predict", 420
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl.replaceAll("/+$", "") + "/api/generate"))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Ollama returned HTTP " + response.statusCode());
        }

        return objectMapper.readTree(response.body()).path("response").asText("").trim();
    }

    private String buildOllamaPrompt(Map<String, Object> summary, List<Map<String, Object>> instances) {
        String topHosts = instances.stream()
                .sorted(Comparator.comparingInt(instance -> -((int) instance.get("riskScore"))))
                .limit(8)
                .map(instance -> String.format(
                        "- host=%s, java=%s, security=%s, risk=%s/%s, apps=%s, installs=%s, jres=%s, os=%s",
                        instance.get("hostname"),
                        instance.get("javaVersion"),
                        instance.get("javaSecurityStatus"),
                        instance.get("riskLevel"),
                        instance.get("riskScore"),
                        instance.get("applicationCount"),
                        instance.get("installationCount"),
                        instance.get("jreCount"),
                        instance.get("osName")
                ))
                .collect(Collectors.joining("\n"));

        return """
                You are an enterprise Java runtime risk analyst.
                Analyze this Oracle Java Management Service fleet data.
                Keep the answer concise, practical, and in English.
                Provide:
                1. Executive summary
                2. Priority actions for the next 7 days
                3. Hosts to inspect first
                4. Caveats

                Fleet summary:
                fleetName=%s
                totalManagedInstances=%s
                overallRiskScore=%s
                criticalCount=%s
                highCount=%s
                mediumCount=%s
                lowCount=%s
                topRiskHost=%s

                Host details:
                %s
                """.formatted(
                summary.get("fleetName"),
                summary.get("totalManagedInstances"),
                summary.get("overallRiskScore"),
                summary.get("criticalCount"),
                summary.get("highCount"),
                summary.get("mediumCount"),
                summary.get("lowCount"),
                summary.get("topRiskHost"),
                topHosts.isBlank() ? "(no managed instances)" : topHosts
        );
    }

    private String buildRuleBasedFleetAnalysis(Map<String, Object> summary, List<Map<String, Object>> instances) {
        if (instances.isEmpty()) {
            return "No managed instances are loaded. Export JMS data or upload managed-instances.json before running AI analysis.";
        }

        int updateRequired = countBySecurityStatus(instances, "UPDATE_REQUIRED");
        int java8Count = (int) instances.stream()
                .filter(instance -> String.valueOf(instance.get("javaVersion")).startsWith("1.8"))
                .count();
        List<Map<String, Object>> topHosts = instances.stream()
                .sorted(Comparator.comparingInt(instance -> -((int) instance.get("riskScore"))))
                .limit(3)
                .toList();

        String topHostList = topHosts.stream()
                .map(instance -> String.format("%s (%s %s, Java %s)",
                        instance.get("hostname"),
                        instance.get("riskLevel"),
                        instance.get("riskScore"),
                        instance.get("javaVersion")))
                .collect(Collectors.joining(", "));

        return """
                Executive summary: Fleet %s has %s managed instances with an overall risk score of %s. Current distribution is Critical=%s, High=%s, Medium=%s, Low=%s.

                Priority actions: Review Java runtimes with UPDATE_REQUIRED first, then validate Java 8 workloads that have application or JRE usage. Patch candidates should be tested in staging before production rollout.

                Hosts to inspect first: %s.

                Caveats: This is rule-based analysis. Start Ollama and set OLLAMA_MODEL if you want generated natural-language analysis from a local model.
                """.formatted(
                summary.get("fleetName"),
                summary.get("totalManagedInstances"),
                summary.get("overallRiskScore"),
                summary.get("criticalCount"),
                summary.get("highCount"),
                summary.get("mediumCount"),
                summary.get("lowCount"),
                topHostList.isBlank() ? summary.get("topRiskHost") : topHostList
        ) + "\nSignals: UPDATE_REQUIRED=" + updateRequired + ", Java 8 runtimes=" + java8Count + ".";
    }

    private int countBySecurityStatus(List<Map<String, Object>> instances, String securityStatus) {
        return (int) instances.stream()
                .filter(instance -> securityStatus.equalsIgnoreCase(String.valueOf(instance.get("javaSecurityStatus"))))
                .count();
    }

    private String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private List<InstallationUsage> summarizeInstallations(
            JavaManagementServiceClient client,
            String fleetId,
            String managedInstanceId
    ) {
        List<InstallationUsage> installations = new ArrayList<>();
        String page = null;
        do {
            SummarizeInstallationUsageResponse response = client.summarizeInstallationUsage(
                    SummarizeInstallationUsageRequest.builder()
                            .fleetId(fleetId)
                            .managedInstanceId(managedInstanceId)
                            .limit(1000)
                            .page(page)
                            .build());
            installations.addAll(response.getInstallationUsageCollection().getItems());
            page = response.getOpcNextPage();
        } while (page != null && !page.isBlank());
        return installations;
    }

    private List<Map<String, Object>> installationRows(JsonNode item) {
        JsonNode installationNodes = firstPresent(item, "java-installations", "javaInstallations");
        List<Map<String, Object>> installations = new ArrayList<>();
        if (!installationNodes.isArray()) {
            return installations;
        }

        for (JsonNode installation : installationNodes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", textAny(installation, "version", "jre-version", "jreVersion"));
            row.put("vendor", textAny(installation, "vendor", "jre-vendor", "jreVendor"));
            row.put("distribution", textAny(installation, "distribution", "jre-distribution", "jreDistribution"));
            row.put("securityStatus", textAny(installation, "security-status", "securityStatus"));
            row.put("path", text(installation, "path"));
            row.put("architecture", text(installation, "architecture"));
            row.put("applicationCount", intAny(installation, "approximate-application-count", "approximateApplicationCount"));
            installations.add(row);
        }
        return installations;
    }

    private ObjectNode toDashboardJson(ManagedInstanceUsage item, List<InstallationUsage> installations) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("hostname", valueOrEmpty(item.getHostname()));
        node.put("managedInstanceId", valueOrEmpty(item.getManagedInstanceId()));
        node.put("managedInstanceType", valueOrEmpty(item.getManagedInstanceType()));
        node.put("approximateApplicationCount", valueOrZero(item.getApproximateApplicationCount()));
        node.put("approximateInstallationCount", valueOrZero(item.getApproximateInstallationCount()));
        node.put("approximateJreCount", valueOrZero(item.getApproximateJreCount()));
        node.put("timeFirstSeen", formatDate(item.getTimeFirstSeen()));
        node.put("timeLastSeen", formatDate(item.getTimeLastSeen()));

        Agent agent = item.getAgent();
        ObjectNode agentNode = objectMapper.createObjectNode();
        if (agent != null) {
            agentNode.put("displayName", valueOrEmpty(agent.getDisplayName()));
            agentNode.put("type", valueOrEmpty(agent.getType()));
            agentNode.put("javaVersion", valueOrEmpty(agent.getJavaVersion()));
            agentNode.put("javaSecurityStatus", valueOrEmpty(agent.getJavaSecurityStatus()));
        }
        node.set("agent", agentNode);

        OperatingSystem operatingSystem = item.getOperatingSystem();
        ObjectNode osNode = objectMapper.createObjectNode();
        if (operatingSystem != null) {
            osNode.put("family", valueOrEmpty(operatingSystem.getFamily()));
            osNode.put("name", valueOrEmpty(operatingSystem.getName()));
            osNode.put("distribution", valueOrEmpty(operatingSystem.getDistribution()));
            osNode.put("version", valueOrEmpty(operatingSystem.getVersion()));
            osNode.put("architecture", valueOrEmpty(operatingSystem.getArchitecture()));
        }
        node.set("operatingSystem", osNode);

        ArrayNode installationNodes = objectMapper.createArrayNode();
        for (InstallationUsage installation : installations) {
            ObjectNode installationNode = objectMapper.createObjectNode();
            installationNode.put("version", valueOrEmpty(installation.getJreVersion()));
            installationNode.put("vendor", valueOrEmpty(installation.getJreVendor()));
            installationNode.put("distribution", valueOrEmpty(installation.getJreDistribution()));
            installationNode.put("securityStatus", valueOrEmpty(installation.getSecurityStatus()));
            installationNode.put("path", valueOrEmpty(installation.getPath()));
            installationNode.put("architecture", valueOrEmpty(installation.getArchitecture()));
            installationNode.put("approximateApplicationCount", valueOrZero(installation.getApproximateApplicationCount()));
            installationNodes.add(installationNode);
        }
        node.set("javaInstallations", installationNodes);

        return node;
    }

    private String valueOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatDate(Date value) {
        return value == null ? "" : value.toInstant().toString();
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private String textAny(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private int intAny(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                return value.asInt(0);
            }
        }
        return 0;
    }

    private JsonNode firstPresent(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return objectMapper.createObjectNode();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
