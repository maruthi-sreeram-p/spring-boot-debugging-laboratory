package com.riverstone.orderevents.controller;

import com.riverstone.orderevents.dto.EventLogResponse;
import com.riverstone.orderevents.dto.StockResponse;
import com.riverstone.orderevents.mapper.PipelineMapper;
import com.riverstone.orderevents.repository.EventLogRepository;
import com.riverstone.orderevents.repository.StockItemRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private final EventLogRepository eventLogRepository;
    private final StockItemRepository stockItemRepository;
    private final PipelineMapper pipelineMapper;

    public PlatformController(EventLogRepository eventLogRepository,
                              StockItemRepository stockItemRepository,
                              PipelineMapper pipelineMapper) {
        this.eventLogRepository = eventLogRepository;
        this.stockItemRepository = stockItemRepository;
        this.pipelineMapper = pipelineMapper;
    }

    /**
     * Every event any consumer has taken off a topic, with the partition and offset it came
     * from and the consumer that handled it.
     */
    @GetMapping("/events")
    @Transactional(readOnly = true)
    public List<EventLogResponse> events(@RequestParam(required = false) String topic) {
        return pipelineMapper.toEventResponses(topic == null
                ? eventLogRepository.findAllByOrderByCreatedAtAsc()
                : eventLogRepository.findByTopicOrderByCreatedAtAsc(topic));
    }

    @GetMapping("/stock")
    @Transactional(readOnly = true)
    public List<StockResponse> stock() {
        return pipelineMapper.toStockResponses(stockItemRepository.findAllByOrderBySkuAsc());
    }
}
