package com.framebucket.gallery;

import com.framebucket.gallery.GuestEventDtos.EventContext;
import com.framebucket.gallery.GuestEventDtos.GuestDeleteRequest;
import com.framebucket.gallery.GuestEventDtos.RegistrationResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/public/event-galleries/{token}")
public class EventGuestController {
    private final EventGuestService guestService;

    public EventGuestController(EventGuestService guestService) {
        this.guestService = guestService;
    }

    @GetMapping
    public ResponseEntity<EventContext> context(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(guestService.publicContext(token));
    }

    @PostMapping(value = "/guests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RegistrationResponse> register(
            @PathVariable String token,
            @RequestParam String name,
            @RequestParam String mobileNumber,
            @RequestParam boolean faceConsent,
            @RequestParam boolean whatsappConsent,
            @RequestParam("selfie") MultipartFile selfie
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(guestService.register(token, name, mobileNumber, faceConsent, whatsappConsent, selfie));
    }

    @DeleteMapping("/guests")
    public ResponseEntity<Void> deleteGuest(@PathVariable String token, @RequestBody GuestDeleteRequest request) {
        guestService.deleteGuest(token, request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).cacheControl(CacheControl.noStore()).build();
    }
}
