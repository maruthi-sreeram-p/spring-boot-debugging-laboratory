package com.athenaeum.lending.controller;

import com.athenaeum.lending.dto.BookResponse;
import com.athenaeum.lending.dto.CopyResponse;
import com.athenaeum.lending.dto.ReservationResponse;
import com.athenaeum.lending.service.CatalogueService;
import com.athenaeum.lending.service.ReservationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalogue")
public class CatalogueController {

    private final CatalogueService catalogueService;
    private final ReservationService reservationService;

    public CatalogueController(CatalogueService catalogueService,
                               ReservationService reservationService) {
        this.catalogueService = catalogueService;
        this.reservationService = reservationService;
    }

    @GetMapping("/books")
    public List<BookResponse> search(@RequestParam(required = false) String term) {
        return catalogueService.search(term);
    }

    @GetMapping("/books/{bookId}")
    public BookResponse book(@PathVariable Long bookId) {
        return catalogueService.byId(bookId);
    }

    @GetMapping("/books/{bookId}/copies")
    public List<CopyResponse> copies(@PathVariable Long bookId) {
        return catalogueService.copiesOf(bookId);
    }

    @GetMapping("/books/{bookId}/holds")
    public List<ReservationResponse> holds(@PathVariable Long bookId) {
        return reservationService.queueFor(bookId);
    }
}
