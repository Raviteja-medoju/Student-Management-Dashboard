package com.example.studentmgmt.controller;

import com.example.studentmgmt.dto.StatsResponse;
import com.example.studentmgmt.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "Dashboard", description = "Aggregated numbers for the dashboard")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    @Operation(summary = "Totals, seat utilization, popular courses and recent students")
    public StatsResponse get() {
        return statsService.getStats();
    }
}
