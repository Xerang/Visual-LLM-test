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
        ImageResponse base = imageService.signedResponse(upload.image());   // 아직 어디에도 공개되지 않은 사진
        ImageResponse body = new ImageResponse(base.id(), base.url(), base.thumbnailUrl(),
                base.width(), base.height(), upload.duplicate());
        return ResponseEntity.status(upload.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(body);
    }

    /**
     * 공개 사진이거나 서명 주소가 맞을 때만 내준다(임시 결정 #9). 그 밖은 404.
     * 공개 사진은 하루, 서명 사진은 본인 브라우저에만 1시간 보관하게 한다.
     */
    @GetMapping("/{imageId}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long imageId,
                                       @RequestParam(required = false) String size,
                                       @RequestParam(required = false) Long exp,
                                       @RequestParam(required = false) String sig) {
        ImageService.ServedFile served = imageService.serve(imageId, "thumb".equals(size), exp, sig);
        CacheControl cache = served.publicImage()
                ? CacheControl.maxAge(Duration.ofDays(1)).cachePublic()
                : CacheControl.maxAge(Duration.ofHours(1)).cachePrivate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(served.file().mimeType()))
                .cacheControl(cache)
                .body(served.file().bytes());
    }
}
