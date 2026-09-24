package org.chimeramc.client.core.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Paging arithmetic for the Installations archive. */
public class ListingPagerTest {

    private static final String P1 = "https://mcpedl.org/downloading/";

    private static String page(int n) {
        return "https://mcpedl.org/downloading/page/" + n + "/";
    }

    @Test
    public void startsOnPageOneWithNothingBehindIt() {
        ListingPager pager = new ListingPager(P1);

        assertEquals(1, pager.pageNumber());
        assertEquals(P1, pager.currentPageUrl());
        assertFalse(pager.hasPrevious());
        assertFalse(pager.hasNext());
    }

    /**
     * The page number has to move with the page. Reading the count before pushing the new page
     * left the label on "Page 1" and Back disabled after pressing Next.
     */
    @Test
    public void advancingMovesToTheNextPageNumber() {
        ListingPager pager = new ListingPager(P1);
        pager.onPageLoaded(page(2));

        assertEquals(page(2), pager.advance());

        assertEquals(2, pager.pageNumber());
        assertEquals(page(2), pager.currentPageUrl());
        assertTrue(pager.hasPrevious());
    }

    @Test
    public void walksForwardThroughSeveralPages() {
        ListingPager pager = new ListingPager(P1);
        pager.onPageLoaded(page(2));
        pager.advance();
        pager.onPageLoaded(page(3));
        pager.advance();
        pager.onPageLoaded(page(4));

        assertEquals(3, pager.pageNumber());
        assertEquals(page(3), pager.currentPageUrl());
        assertEquals(page(4), pager.nextPageUrl());
        assertTrue(pager.hasNext());
    }

    @Test
    public void stopsAtTheLastPage() {
        ListingPager pager = new ListingPager(P1);
        pager.onPageLoaded(page(2));
        pager.advance();
        pager.onPageLoaded(null);

        assertEquals(2, pager.pageNumber());
        assertFalse(pager.hasNext());
        assertNull(pager.advance());
        assertEquals(2, pager.pageNumber());
    }

    @Test
    public void retreatsToThePreviousPage() {
        ListingPager pager = new ListingPager(P1);
        pager.onPageLoaded(page(2));
        pager.advance();
        pager.onPageLoaded(page(3));
        pager.advance();
        pager.onPageLoaded(page(4));

        assertEquals(page(2), pager.retreat());
        assertEquals(2, pager.pageNumber());

        assertEquals(P1, pager.retreat());
        assertEquals(1, pager.pageNumber());
        assertFalse(pager.hasPrevious());
        assertNull(pager.retreat());
    }

    /** Walking back and then forward again must land on the same page, not skip one. */
    @Test
    public void returningForwardRevisitsTheSamePage() {
        ListingPager pager = new ListingPager(P1);
        pager.onPageLoaded(page(2));
        pager.advance();
        pager.onPageLoaded(page(3));

        pager.retreat();
        assertEquals(1, pager.pageNumber());

        pager.onPageLoaded(page(2));
        assertEquals(page(2), pager.advance());
        assertEquals(2, pager.pageNumber());
    }

    @Test
    public void resetReturnsToTheFirstPage() {
        ListingPager pager = new ListingPager(P1);
        pager.onPageLoaded(page(2));
        pager.advance();

        pager.reset();

        assertEquals(1, pager.pageNumber());
        assertEquals(P1, pager.currentPageUrl());
        assertFalse(pager.hasPrevious());
        assertFalse(pager.hasNext());
    }

    /**
     * A configuration change must not drop the reader back on page 1 mid-browse; rebuilding
     * from the saved walk is what keeps Next/Back consistent after recreation.
     */
    @Test
    public void restoresASavedWalk() {
        ListingPager original = new ListingPager(P1);
        original.onPageLoaded(page(2));
        original.advance();
        original.onPageLoaded(page(3));

        ListingPager restored = new ListingPager(
                P1, original.visitedPages(), original.nextPageUrl());

        assertEquals(2, restored.pageNumber());
        assertEquals(page(2), restored.currentPageUrl());
        assertTrue(restored.hasPrevious());
        assertEquals(page(3), restored.advance());
        assertEquals(3, restored.pageNumber());
    }

    @Test
    public void fallsBackToTheFirstPageWithoutSavedState() {
        ListingPager pager = new ListingPager(P1, null, null);

        assertEquals(1, pager.pageNumber());
        assertFalse(pager.hasPrevious());
        assertFalse(pager.hasNext());
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesToStartWithoutAFirstPage() {
        new ListingPager("");
    }
}
