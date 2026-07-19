package com.tontine.controller;

import com.tontine.service.AssistantService;
import com.tontine.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/assistant")
@RequiredArgsConstructor
@Tag(name = "Assistant", description = "Assistant d'aide IA — questions sur l'utilisation de l'application")
public class AssistantController {

    private final AssistantService assistantService;
    private final SecurityUtil securityUtil;

    @Data
    public static class QuestionRequest {
        @NotBlank
        @Size(max = 500)
        private String question;
    }

    @PostMapping("/question")
    @Operation(summary = "Poser une question à l'assistant Adashe (IA + guide d'utilisation)")
    public ResponseEntity<Map<String, String>> question(@RequestBody QuestionRequest request) {
        String reponse = assistantService.repondre(request.getQuestion(), securityUtil.getCurrentUserId());
        return ResponseEntity.ok(Map.of("reponse", reponse));
    }
}
