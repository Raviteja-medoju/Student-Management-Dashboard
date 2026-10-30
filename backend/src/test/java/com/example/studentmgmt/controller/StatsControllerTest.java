package com.example.studentmgmt.controller;

import com.example.studentmgmt.dto.StatsResponse;
import com.example.studentmgmt.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatsService statsService;

    @Test
    void get_returnsDashboardNumbers() throws Exception {
        when(statsService.getStats()).thenReturn(new StatsResponse(12, 9, 31, 8.4, 11, List.of(), List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents").value(12))
                .andExpect(jsonPath("$.averageCreditsPerStudent").value(8.4))
                .andExpect(jsonPath("$.seatUtilizationPercent").value(11));
    }
}
