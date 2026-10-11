package com.bozor.image;

import com.bozor.auth.LoginUser;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/images")
public class ImageController {
    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    /** 새 사진이면 201, 이미 올린 같은 파일이면 200 + duplicate=true. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponse> upload(@LoginUser Long userId, @RequestPart("file") MultipartFile file) {
        ImageService.Upload upload = imageService.upload(userId, file);
        ImageResponse base = ImageService.response(upload.image());
        ImageResponse body = new ImageResponse(base.id(), base.url(), base.thumbnailUrl(),
                base.width(), base.height(), upload.duplicate());
        return ResponseEntity.status(upload.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(body);
    }

    /** 지금은 id만 알면 누구나 볼 수 있다(임시 결정 #9). */
    @GetMapping("/{imageId}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long imageId, @RequestParam(required = false) String size) {
        ImageService.StoredFile file = imageService.file(imageId, "thumb".equals(size));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(file.bytes());
    }
}
