package org.example.controller;

import org.example.config.FileUploadConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@RestController
public class KnowledgeBaseController {

    private final FileUploadConfig fileUploadConfig;

    public KnowledgeBaseController(FileUploadConfig fileUploadConfig) {
        this.fileUploadConfig = fileUploadConfig;
    }

    @GetMapping("/api/knowledge/files")
    public ResponseEntity<FileUploadController.ApiResponse<List<KnowledgeFile>>> listFiles() throws IOException {
        Path uploadDir = Paths.get(fileUploadConfig.getPath()).normalize();
        FileUploadController.ApiResponse<List<KnowledgeFile>> response = new FileUploadController.ApiResponse<>();

        if (!Files.exists(uploadDir)) {
            response.setCode(200);
            response.setMessage("success");
            response.setData(List.of());
            return ResponseEntity.ok(response);
        }

        try (Stream<Path> stream = Files.list(uploadDir)) {
            List<KnowledgeFile> files = stream
                    .filter(Files::isRegularFile)
                    .map(this::toKnowledgeFile)
                    .sorted(Comparator.comparing(KnowledgeFile::getLastModified).reversed())
                    .toList();

            response.setCode(200);
            response.setMessage("success");
            response.setData(files);
            return ResponseEntity.ok(response);
        }
    }

    private KnowledgeFile toKnowledgeFile(Path path) {
        try {
            String fileName = path.getFileName().toString();
            return new KnowledgeFile(
                    fileName,
                    path.toString(),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toMillis(),
                    extensionOf(fileName)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file metadata: " + path, e);
        }
    }

    private String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    public static class KnowledgeFile {
        private String fileName;
        private String filePath;
        private long fileSize;
        private long lastModified;
        private String extension;

        public KnowledgeFile(String fileName, String filePath, long fileSize, long lastModified, String extension) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.lastModified = lastModified;
            this.extension = extension;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public long getLastModified() {
            return lastModified;
        }

        public void setLastModified(long lastModified) {
            this.lastModified = lastModified;
        }

        public String getExtension() {
            return extension;
        }

        public void setExtension(String extension) {
            this.extension = extension;
        }
    }
}
