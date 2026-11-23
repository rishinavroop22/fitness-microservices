package com.enterprise.knowledge.document.service;

import com.enterprise.knowledge.document.config.KnowledgeProperties;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/** Extracts plain text from PDF, Word, PowerPoint, HTML, Markdown and other formats using Apache Tika. */
@Service
public class TextExtractionService {

    private final Tika tika = new Tika();

    public TextExtractionService(KnowledgeProperties properties) {
        tika.setMaxStringLength(properties.extraction().maxCharacters());
    }

    public String detectType(InputStream stream, String fileName) throws IOException {
        return tika.detect(stream, fileName);
    }

    public String extract(InputStream stream) throws IOException {
        try {
            return normalise(tika.parseToString(stream));
        } catch (TikaException e) {
            throw new DocumentProcessingException("Could not extract text: " + e.getMessage(), e);
        }
    }

    static String normalise(String text) {
        return text.replace("\r\n", "\n")
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
