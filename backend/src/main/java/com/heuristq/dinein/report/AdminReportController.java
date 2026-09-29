package com.heuristq.dinein.report;

import com.heuristq.dinein.report.dto.ReportDtos.Dashboard;
import com.heuristq.dinein.report.dto.ReportDtos.SalesSummary;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Today's operational view, for owners and managers. */
    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return reportService.dashboard();
    }

    @GetMapping("/reports/summary")
    @PreAuthorize("@perm.has('VIEW_REPORTS')")
    public SalesSummary summary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.summary(from, to);
    }

    @GetMapping("/reports/orders.csv")
    @PreAuthorize("@perm.has('VIEW_REPORTS')")
    public void ordersCsv(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("orders-" + from + "-to-" + to + ".csv").build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        reportService.writeOrdersCsv(from, to, writer);
    }
}
