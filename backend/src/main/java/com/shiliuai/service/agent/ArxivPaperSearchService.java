package com.shiliuai.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.dto.PaperDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ArxivPaperSearchService implements PaperSearchService {
    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    private static final int CACHE_LIMIT = 32;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, List<PaperDto>> cache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<PaperDto>> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    public ArxivPaperSearchService(RestClient.Builder builder, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = builder.requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PaperDto> search(String command, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        String cacheKey = normalizedSearchText(command) + ":" + safeLimit;
        synchronized (cache) {
            List<PaperDto> cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        List<PaperDto> papers = searchWithFallback(command, safeLimit);
        synchronized (cache) {
            cache.put(cacheKey, papers);
        }
        return papers;
    }

    private List<PaperDto> searchWithFallback(String command, int limit) {
        try {
            return searchArxiv(command, limit);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 429) {
                throw exception;
            }
            return searchOpenAlex(command, limit);
        } catch (RuntimeException exception) {
            List<PaperDto> fallback = searchOpenAlex(command, limit);
            if (!fallback.isEmpty()) {
                return fallback;
            }
            throw exception;
        }
    }

    private List<PaperDto> searchArxiv(String command, int limit) {
        String query = arxivQuery(command);
        URI uri = UriComponentsBuilder.fromUriString("https://export.arxiv.org/api/query")
                .queryParam("search_query", query)
                .queryParam("start", 0)
                .queryParam("max_results", limit)
                .queryParam("sortBy", "relevance")
                .queryParam("sortOrder", "descending")
                .build()
                .encode()
                .toUri();
        String xml = restClient.get().uri(uri).retrieve().body(String.class);
        if (!StringUtils.hasText(xml)) {
            return List.of();
        }
        return parse(xml);
    }

    private List<PaperDto> searchOpenAlex(String command, int limit) {
        URI uri = UriComponentsBuilder.fromUriString("https://api.openalex.org/works")
                .queryParam("search", normalizedSearchText(command))
                .queryParam("per-page", limit)
                .build()
                .encode()
                .toUri();
        JsonNode response = restClient.get().uri(uri).retrieve().body(JsonNode.class);
        JsonNode results = response == null ? objectMapper.createArrayNode() : response.path("results");
        List<PaperDto> papers = new ArrayList<>();
        if (!results.isArray()) {
            return papers;
        }
        for (JsonNode item : results) {
            PaperDto paper = new PaperDto();
            paper.title = item.path("display_name").asText("");
            paper.year = item.path("publication_year").isMissingNode() ? "" : item.path("publication_year").asText("");
            paper.venue = openAlexVenue(item);
            paper.url = openAlexUrl(item);
            paper.contribution = truncate(openAlexAbstract(item), 360);
            paper.whyRelevant = "来自 OpenAlex 真实检索结果，作为 arXiv 限流或无结果时的兜底数据源。";
            paper.tags = openAlexTags(item);
            if (StringUtils.hasText(paper.title)) {
                papers.add(paper);
            }
        }
        return papers;
    }

    private static String arxivQuery(String command) {
        String text = normalizedSearchText(command);
        if (!StringUtils.hasText(text)) {
            text = "benchmark evaluation";
        }
        List<String> tokens = new ArrayList<>();
        for (String token : text.split("\\s+")) {
            if (token.length() >= 3 && tokens.stream().noneMatch(token::equals)) {
                tokens.add(token);
            }
            if (tokens.size() >= 6) {
                break;
            }
        }
        if (tokens.isEmpty()) {
            tokens.add("benchmark");
            tokens.add("evaluation");
        }
        return tokens.stream()
                .map(token -> "all:" + token)
                .reduce((left, right) -> left + " AND " + right)
                .orElse("all:benchmark");
    }

    private static String normalizedSearchText(String command) {
        String text = command == null ? "" : command;
        text = text.replace("/paper", " ")
                .replace("收集", " ")
                .replace("整理", " ")
                .replace("论文", " ")
                .replace("相关", " ")
                .replace("贡献点", " ")
                .replace("阅读任务", " ")
                .replace("测评", " evaluation ")
                .replace("评测", " evaluation ")
                .replaceAll("[^a-zA-Z0-9\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            text = "benchmark evaluation";
        }
        return text;
    }


    private static List<PaperDto> parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList entries = document.getElementsByTagNameNS(ATOM_NS, "entry");
            List<PaperDto> papers = new ArrayList<>();
            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                PaperDto paper = new PaperDto();
                paper.title = normalize(text(entry, "title"));
                String published = text(entry, "published");
                paper.year = published.length() >= 4 ? published.substring(0, 4) : "";
                paper.venue = category(entry);
                paper.url = text(entry, "id");
                paper.contribution = truncate(normalize(text(entry, "summary")), 360);
                paper.whyRelevant = "来自 arXiv 真实检索结果，匹配当前论文收集命令。";
                paper.tags = categories(entry);
                papers.add(paper);
            }
            return papers;
        } catch (Exception exception) {
            throw new IllegalStateException("解析 arXiv Atom 响应失败：" + exception.getMessage(), exception);
        }
    }

    private static String text(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagNameNS(ATOM_NS, tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private static String category(Element entry) {
        List<String> categories = categories(entry);
        return categories.isEmpty() ? "arXiv" : "arXiv:" + categories.get(0);
    }

    private static List<String> categories(Element entry) {
        NodeList nodes = entry.getElementsByTagNameNS(ATOM_NS, "category");
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element category = (Element) nodes.item(i);
            String term = category.getAttribute("term");
            if (StringUtils.hasText(term)) {
                values.add(term);
            }
        }
        return values;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String openAlexVenue(JsonNode item) {
        String source = item.path("primary_location").path("source").path("display_name").asText("");
        return StringUtils.hasText(source) ? source : "OpenAlex";
    }

    private static String openAlexUrl(JsonNode item) {
        String doi = item.path("doi").asText("");
        if (StringUtils.hasText(doi)) {
            return doi;
        }
        String landingPage = item.path("primary_location").path("landing_page_url").asText("");
        if (StringUtils.hasText(landingPage)) {
            return landingPage;
        }
        String pdf = item.path("primary_location").path("pdf_url").asText("");
        if (StringUtils.hasText(pdf)) {
            return pdf;
        }
        return item.path("id").asText("");
    }

    private static String openAlexAbstract(JsonNode item) {
        JsonNode inverted = item.path("abstract_inverted_index");
        if (!inverted.isObject()) {
            String title = item.path("display_name").asText("");
            return StringUtils.hasText(title) ? "OpenAlex 未提供摘要倒排索引，先基于标题和来源进行初筛。" : "";
        }
        Map<Integer, String> words = new LinkedHashMap<>();
        inverted.fields().forEachRemaining(entry -> {
            for (JsonNode index : entry.getValue()) {
                words.put(index.asInt(), entry.getKey());
            }
        });
        return words.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private static List<String> openAlexTags(JsonNode item) {
        List<String> tags = new ArrayList<>();
        JsonNode concepts = item.path("concepts");
        if (concepts.isArray()) {
            for (JsonNode concept : concepts) {
                String name = concept.path("display_name").asText("");
                if (StringUtils.hasText(name)) {
                    tags.add(name);
                }
                if (tags.size() >= 4) {
                    return tags;
                }
            }
        }
        return tags;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}
