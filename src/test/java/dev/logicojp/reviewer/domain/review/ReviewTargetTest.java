package dev.logicojp.reviewer.domain.review;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReviewTargetTest {

    @Test
    void gitHub_createsGitHubTarget() {
        ReviewTarget target = ReviewTarget.gitHub("owner/repo");
        assertInstanceOf(ReviewTarget.GitHubTarget.class, target);
        assertEquals("owner/repo", ((ReviewTarget.GitHubTarget) target).repository());
    }

    @Test
    void gitHub_rejectsInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> ReviewTarget.gitHub("no-slash"));
        assertThrows(IllegalArgumentException.class, () -> ReviewTarget.gitHub(""));
        assertThrows(IllegalArgumentException.class, () -> ReviewTarget.gitHub(null));
    }

    @Test
    void local_createsLocalTarget() {
        Path dir = Path.of("/some/dir");
        ReviewTarget target = ReviewTarget.local(dir);
        assertInstanceOf(ReviewTarget.LocalTarget.class, target);
        assertEquals(dir, ((ReviewTarget.LocalTarget) target).directory());
    }

    @Test
    void displayName_gitHub() {
        assertEquals("owner/repo", ReviewTarget.gitHub("owner/repo").displayName());
    }

    @Test
    void displayName_local() {
        assertEquals("project", ReviewTarget.local(Path.of("/some/project")).displayName());
    }

    @Test
    void isLocal_returnsTrueOnlyForLocal() {
        assertTrue(ReviewTarget.local(Path.of("/dir")).isLocal());
        assertFalse(ReviewTarget.gitHub("owner/repo").isLocal());
    }

    @Test
    void localPath_returnsPathForLocal() {
        Path dir = Path.of("/dir");
        var opt = ReviewTarget.local(dir).localPath();
        assertTrue(opt.isPresent());
        assertEquals(dir, opt.get());
    }

    @Test
    void localPath_returnsEmptyForGitHub() {
        assertTrue(ReviewTarget.gitHub("owner/repo").localPath().isEmpty());
    }
}
