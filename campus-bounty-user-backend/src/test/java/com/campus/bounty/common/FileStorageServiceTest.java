package com.campus.bounty.common;

import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class FileStorageServiceTest {
    private static final Path UPLOAD_DIR = Path.of(System.getProperty("java.io.tmpdir"),
            "campus-bounty-upload-test-" + UUID.randomUUID());

    @DynamicPropertySource
    static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", UPLOAD_DIR::toString);
    }

    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    FileStorageService storageService;
    @Autowired
    UserService userService;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void stores_allowed_image_types_with_uuid_names() throws Exception {
        for (String extension : new String[]{"jpg", "jpeg", "png", "gif", "webp"}) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "picture." + extension, "image/" + extension, new byte[]{1, 2, 3});

            String storedPath = storageService.store(file);

            assertTrue(storedPath.matches("/uploads/[0-9a-f-]{36}\\." + extension));
            assertArrayEquals(new byte[]{1, 2, 3},
                    Files.readAllBytes(UPLOAD_DIR.resolve(storedPath.substring("/uploads/".length()))));
        }
    }

    @Test
    void rejects_disallowed_empty_and_oversized_files() {
        MockMultipartFile executable = new MockMultipartFile(
                "file", "virus.exe", "application/octet-stream", new byte[]{1});
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

        assertThrows(BizException.class, () -> storageService.store(executable));
        assertThrows(BizException.class, () -> storageService.store(empty));
        assertThrows(BizException.class, () -> storageService.store(oversized));
    }

    @Test
    void stored_image_is_available_anonymously_over_http() {
        String storedPath = storageService.store(new MockMultipartFile(
                "file", "public.png", "image/png", new byte[]{4, 5, 6}));

        ResponseEntity<byte[]> response = rest.getForEntity(url(storedPath), byte[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(new byte[]{4, 5, 6}, response.getBody());
    }

    @Test
    void authenticated_user_can_upload_image() {
        User user = userService.register("upload_http_user", "123456", "123456", "上传测试");
        ByteArrayResource image = new ByteArrayResource(new byte[]{7, 8, 9}) {
            @Override
            public String getFilename() {
                return "avatar.png";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", image);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(jwtUtil.generate(user.getId(), user.getUsername()));

        ResponseEntity<Map> response = rest.postForEntity(url("/api/community/upload"),
                new HttpEntity<>(body, headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, ((Number) response.getBody().get("code")).intValue());
        assertTrue(response.getBody().get("data").toString().matches("/uploads/[0-9a-f-]{36}\\.png"));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
