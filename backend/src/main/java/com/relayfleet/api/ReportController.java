package com.relayfleet.api;

import com.relayfleet.service.FleetReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final FleetReportService reports;

    public ReportController(FleetReportService reports) { this.reports = reports; }

    @GetMapping(value = "/fleet.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf() { return file(reports.pdf(), MediaType.APPLICATION_PDF, "relay-fleet.pdf"); }

    @GetMapping(value = "/fleet.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> excel() { return file(reports.excel(), MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "relay-fleet.xlsx"); }

    @GetMapping(value = "/fleet.docx", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> word() { return file(reports.word(), MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), "relay-fleet.docx"); }

    private static ResponseEntity<byte[]> file(byte[] bytes, MediaType mediaType, String filename) {
        return ResponseEntity.ok().contentType(mediaType).header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"").body(bytes);
    }
}