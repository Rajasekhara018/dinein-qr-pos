package com.heuristq.dinein.table;

import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.dto.TableDtos.TableRequest;
import com.heuristq.dinein.table.dto.TableDtos.TableResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tables")
public class AdminTableController {

    private final TableService tableService;
    private final QrCodeService qrCodeService;
    private final QrPdfService qrPdfService;
    private final SettingsService settingsService;

    public AdminTableController(TableService tableService, QrCodeService qrCodeService, QrPdfService qrPdfService,
                                SettingsService settingsService) {
        this.tableService = tableService;
        this.qrCodeService = qrCodeService;
        this.qrPdfService = qrPdfService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public List<TableResponse> list() {
        return tableService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse create(@Valid @RequestBody TableRequest request) {
        return tableService.create(request);
    }

    /** Declared before /{id} so "qr.pdf" is never parsed as an id. */
    @GetMapping(value = "/qr.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> qrPdf(@RequestParam(required = false) List<Long> ids) {
        List<DiningTableEntity> tables = tableService.findForPrint(ids);
        byte[] pdf = qrPdfService.render(tables, settingsService.current().getName(), tableService::menuUrl);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("table-qr-codes.pdf").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(pdf);
    }

    @PutMapping("/{id}")
    public TableResponse update(@PathVariable Long id, @Valid @RequestBody TableRequest request) {
        return tableService.update(id, request);
    }

    @PostMapping("/{id}/regenerate-qr")
    public TableResponse regenerate(@PathVariable Long id) {
        return tableService.regenerateQr(id);
    }

    @GetMapping(value = "/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrPng(@PathVariable Long id) {
        DiningTableEntity table = tableService.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(qrCodeService.png(tableService.menuUrl(table), 512));
    }
}
