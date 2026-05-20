package com.jzo2o.ai.controller.consumer;

import com.jzo2o.ai.properties.AiEngineProperties;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 知识库控制器 — 转发 CRUD 请求到 Python AI 引擎
 */
@Slf4j
@RestController
@RequestMapping("/consumer/knowledge")
@Api(tags = "AI助手-知识库管理")
public class KnowledgeController {

    @Resource
    private WebClient webClient;

    @Resource
    private AiEngineProperties aiEngineProperties;

    private String knowledgeBaseUrl() {
        return aiEngineProperties.getBaseUrl() + "/knowledge";
    }

    @PostMapping("/documents")
    @ApiOperation("添加知识库文档")
    public Map<String, Object> createDocument(@RequestBody Map<String, Object> body) {
        log.info("添加知识库文档: title={}", body.get("title"));
        return webClient.post()
                .uri(knowledgeBaseUrl() + "/documents")
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    @GetMapping("/documents/{parentDocId}")
    @ApiOperation("查询单个文档")
    public Map<String, Object> getDocument(@PathVariable String parentDocId) {
        return webClient.get()
                .uri(knowledgeBaseUrl() + "/documents/" + parentDocId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    @PutMapping("/documents/{parentDocId}")
    @ApiOperation("更新文档")
    public Map<String, Object> updateDocument(@PathVariable String parentDocId,
                                              @RequestBody Map<String, Object> body) {
        log.info("更新知识库文档: parentDocId={}", parentDocId);
        return webClient.put()
                .uri(knowledgeBaseUrl() + "/documents/" + parentDocId)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    @DeleteMapping("/documents/{parentDocId}")
    @ApiOperation("删除文档")
    public Map<String, Object> deleteDocument(@PathVariable String parentDocId) {
        log.info("删除知识库文档: parentDocId={}", parentDocId);
        return webClient.delete()
                .uri(knowledgeBaseUrl() + "/documents/" + parentDocId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    @GetMapping("/documents")
    @ApiOperation("分页列出文档")
    public Map<String, Object> listDocuments(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(defaultValue = "") String category) {
        return webClient.get()
                .uri(knowledgeBaseUrl() + "/documents?page=" + page + "&size=" + size + "&category=" + category)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    @PostMapping("/search")
    @ApiOperation("向量检索知识库")
    public Map<String, Object> search(@RequestBody Map<String, Object> body) {
        return webClient.post()
                .uri(knowledgeBaseUrl() + "/search")
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }
}
