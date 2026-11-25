package com.heuristiq.documents.document.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchPatternsTest {

    @Test
    void likeWildcardsAndTheEscapeCharacterAreEscaped() {
        assertEquals("100\\%\\_done\\\\", SearchPatterns.escapeLike("100%_done\\"));
    }

    @Test
    void containsAndPrefixAreLowerCasedAndAnchoredCorrectly() {
        assertEquals("%inv\\_oice%", SearchPatterns.likeContains("Inv_OICE"));
        assertEquals("abc\\%%", SearchPatterns.likePrefix("ABC%"));
    }

    @Test
    void userTextNeverBecomesAWildcard() {
        assertEquals("%\\%%", SearchPatterns.likeContains("%"));
        assertEquals("%\\_%", SearchPatterns.likeContains("_"));
    }
}
