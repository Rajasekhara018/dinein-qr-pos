package com.heuristq.dinein.image;

import com.heuristq.dinein.shared.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/images")
public class AdminImageController {

    private final ImageService imageService;

    public AdminImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageService.UploadResult upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.getSize() > ImageProcessor.MAX_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File must be 5 MB or smaller");
        }
        return imageService.upload(file.getBytes());
    }
}
