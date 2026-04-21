package com.preplit.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Service
public class ResumeParserService {

    private static final int MAX_RESUME_LENGTH = 15000;

    public Mono<String> extractText(FilePart filePart) {
        if (filePart == null) {
            return Mono.error(new IllegalArgumentException("Resume file is required"));
        }

        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "";
        if (!contentType.equals("application/pdf")) {
            return Mono.error(new IllegalArgumentException("Only PDF files are supported"));
        }

        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    try (PDDocument document = Loader.loadPDF(bytes)) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        String text = stripper.getText(document);
                        if (text.length() > MAX_RESUME_LENGTH) {
                            text = text.substring(0, MAX_RESUME_LENGTH) + "\n[Resume truncated...]";
                        }
                        return Mono.just(cleanupText(text));
                    } catch (IOException e) {
                        return Mono.error(new RuntimeException("Failed to parse PDF: " + e.getMessage(), e));
                    }
                });
    }

    private String cleanupText(String text) {
        return text.replaceAll("\\r\\n", "\n")
                   .replaceAll("\\n{3,}", "\n\n")
                   .trim();
    }
}
