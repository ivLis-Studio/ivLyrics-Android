package kr.ivlis.ivlyricsandroid;

import java.io.IOException;

final class PaxsenixAiModels {
    private PaxsenixAiModels() {}

    static void requireSelectedModel(String model) throws IOException {
        if (model == null || model.trim().isEmpty()) {
            throw new IOException("AI model must be selected");
        }
    }
}
