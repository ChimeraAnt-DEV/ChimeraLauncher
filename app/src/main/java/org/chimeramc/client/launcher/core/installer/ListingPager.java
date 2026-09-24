package org.chimeramc.client.core.installer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks which listing page is on screen and where the walk through the archive can go.
 *
 * The archive is dozens of pages deep and is fetched one page at a time, so the screen needs
 * to remember how it got to the current page in order to offer "Back", and the page after it
 * in order to offer "Next". Keeping that bookkeeping here rather than inside the Activity
 * makes it testable without a device: the numbering and the back/forward boundary conditions
 * are exactly the kind of thing that is wrong on screen and invisible in a build.
 *
 * The visited pages are held as a stack whose top is the current page, so the page number is
 * simply the stack depth and stepping back is a pop. Pages are re-fetched when revisited
 * rather than cached, so the list always reflects what the site currently publishes.
 */
public class ListingPager {

    private final String firstPageUrl;
    private final List<String> visited = new ArrayList<>();
    private String nextPageUrl;

    public ListingPager(String firstPageUrl) {
        if (firstPageUrl == null || firstPageUrl.isEmpty()) {
            throw new IllegalArgumentException("A pager needs a first page URL");
        }
        this.firstPageUrl = firstPageUrl;
        visited.add(firstPageUrl);
    }

    /**
     * Rebuilds a pager from a saved walk, so a configuration change or a restored process does
     * not silently drop the reader back on the newest releases mid-browse.
     *
     * A blank or missing walk falls back to the first page, because an unreadable state must
     * still leave the screen usable.
     */
    public ListingPager(String firstPageUrl, List<String> visitedPages, String nextPageUrl) {
        this(firstPageUrl);
        if (visitedPages == null || visitedPages.isEmpty()) return;
        visited.clear();
        for (String url : visitedPages) {
            if (url != null && !url.isEmpty()) visited.add(url);
        }
        if (visited.isEmpty()) visited.add(firstPageUrl);
        this.nextPageUrl = nextPageUrl;
    }

    /** The page currently on screen, 1-based. */
    public int pageNumber() {
        return visited.size();
    }

    public String currentPageUrl() {
        return visited.get(visited.size() - 1);
    }

    public String nextPageUrl() {
        return nextPageUrl;
    }

    public boolean hasPrevious() {
        return visited.size() > 1;
    }

    public boolean hasNext() {
        return nextPageUrl != null && !nextPageUrl.isEmpty();
    }

    /**
     * Records the page link after the one that just loaded.
     *
     * Called from the load result rather than derived up front, because only the fetched page
     * knows the address of the page after it; the last page reports null.
     */
    public void onPageLoaded(String nextPageUrl) {
        this.nextPageUrl = nextPageUrl;
    }

    /**
     * Steps onto the following page.
     *
     * Returns the URL to fetch, or null when this is the last page. The new page is pushed
     * before returning so the page number is already correct while the fetch is in flight.
     */
    public String advance() {
        if (!hasNext()) return null;
        visited.add(nextPageUrl);
        return nextPageUrl;
    }

    /**
     * Steps back onto the page before this one.
     *
     * Returns the URL to fetch, or null when already on the first page. The current page is
     * popped first so the page number reflects the page being returned to.
     */
    public String retreat() {
        if (!hasPrevious()) return null;
        visited.remove(visited.size() - 1);
        return currentPageUrl();
    }

    /** Returns to the first page, discarding the walk taken through the archive. */
    public void reset() {
        visited.clear();
        visited.add(firstPageUrl);
        nextPageUrl = null;
    }

    /** The pages visited so far, oldest first. Exposed for diagnostics. */
    public List<String> visitedPages() {
        return Collections.unmodifiableList(new ArrayList<>(visited));
    }
}
