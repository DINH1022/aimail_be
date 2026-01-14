package com.example.aimailbox.controller;

import com.example.aimailbox.dto.response.KanbanColumnResponse;
import com.example.aimailbox.service.ProxyMailService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kanban/columns")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class KanbanController {
    ProxyMailService proxyMailService;
    @GetMapping()
    public List<KanbanColumnResponse> getKanbanColumns() {
        return proxyMailService.getKanbanColumnsByUserId();
    }
    @DeleteMapping("/{id}")
    public void deleteKanbanColumnById(@PathVariable long id) {
        proxyMailService.deleteKanbanColumn(id);
    }
}
