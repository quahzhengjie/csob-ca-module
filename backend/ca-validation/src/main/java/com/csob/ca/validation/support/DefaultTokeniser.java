package com.csob.ca.validation.support;

import java.util.List;

public final class DefaultTokeniser implements Tokeniser {

    @Override
    public List<DetectedToken> tokenise(String text) {
        throw new UnsupportedOperationException(
                "Skeleton — run the three v1 regex passes and return ordered tokens.");
    }
}
