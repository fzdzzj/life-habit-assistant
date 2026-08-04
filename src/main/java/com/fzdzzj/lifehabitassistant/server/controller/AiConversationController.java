package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.AiConversationDtos;
import com.fzdzzj.lifehabitassistant.pojo.PageResponse;
import com.fzdzzj.lifehabitassistant.server.service.AiConversationService;
import com.fzdzzj.lifehabitassistant.server.service.AiConversationStreamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/conversations")
@Validated
public class AiConversationController {
    private final AiConversationService service;
    private final AiConversationStreamService streamService;

    public AiConversationController(AiConversationService service,
                                    AiConversationStreamService streamService) {
        this.service = service;
        this.streamService = streamService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Result<AiConversationDtos.ConversationResponse> create(
            @Valid @RequestBody(required = false) AiConversationDtos.CreateConversationRequest request) {
        return Result.success(service.create(request));
    }

    @GetMapping
    Result<PageResponse<AiConversationDtos.ConversationResponse>> list(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 不得小于 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 不得小于 1")
            @Max(value = 100, message = "size 不得超过 100") int size) {
        return Result.success(service.list(page, size));
    }

    @GetMapping("/{id}/messages")
    Result<List<AiConversationDtos.MessageResponse>> messages(
            @PathVariable @Positive(message = "id 必须大于 0") Long id) {
        return Result.success(service.messages(id));
    }

    @PostMapping("/{id}/messages")
    Result<AiConversationDtos.SendMessageResponse> send(
            @PathVariable @Positive(message = "id 必须大于 0") Long id,
            @Valid @RequestBody AiConversationDtos.SendMessageRequest request) {
        streamService.cancelActiveForSyncSend(id);
        return Result.success(service.send(id, request));
    }

    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable @Positive(message = "id 必须大于 0") Long id,
                      @Valid @RequestBody AiConversationDtos.SendMessageRequest request) {
        return streamService.start(id, request);
    }

    @PostMapping("/{id}/messages/cancel")
    Result<Void> cancel(@PathVariable @Positive(message = "id 必须大于 0") Long id) {
        streamService.cancelAndFinish(id);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable @Positive(message = "id 必须大于 0") Long id) {
        service.delete(id);
        return Result.success(null);
    }
}
