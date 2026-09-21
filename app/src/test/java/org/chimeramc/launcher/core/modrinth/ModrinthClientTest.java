package org.chimeramc.launcher.core.modrinth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The search URL is the one part of the Modrinth client that can be checked without a
 * network round trip, and it is also where the facet encoding is easiest to get wrong:
 * Modrinth expects a JSON array of arrays, not a flat list of strings.
 */
public class ModrinthClientTest {

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void noFiltersProducesNoFacetsParameter() {
        String url = ModrinthClient.buildSearchUrl("", null, null, 0, 20, null);
        assertFalse(url, url.contains("facets="));
        assertTrue(url, url.contains("index=relevance"));
    }

    @Test
    public void projectTypeFacetIsAJsonArrayOfArrays() {
        String url = ModrinthClient.buildSearchUrl("", ModrinthClient.TYPE_RESOURCEPACK, null, 0, 20, null);
        String facets = decode(url.replaceAll(".*facets=", ""));
        assertEquals("[[\"project_type:resourcepack\"]]", facets);
    }

    @Test
    public void twoFacetsBecomeTwoSeparateGroups() {
        String url = ModrinthClient.buildSearchUrl("", ModrinthClient.TYPE_MODPACK, "1.21.1", 0, 20, null);
        String facets = decode(url.replaceAll(".*facets=", ""));
        assertEquals("[[\"project_type:modpack\"],[\"versions:1.21.1\"]]", facets);
    }

    @Test
    public void gameVersionOnlyStillProducesValidJson() {
        String url = ModrinthClient.buildSearchUrl("", null, "26.1", 0, 20, null);
        String facets = decode(url.replaceAll(".*facets=", ""));
        assertEquals("[[\"versions:26.1\"]]", facets);
        assertEquals("every facet group must be closed", 2,
                facets.chars().filter(c -> c == ']').count());
    }

    @Test
    public void limitIsClampedAndOffsetIsNotNegative() {
        String url = ModrinthClient.buildSearchUrl("", null, null, -5, 500, null);
        assertTrue(url, url.contains("limit=100"));
        assertTrue(url, url.contains("offset=0"));
    }

    @Test
    public void queryAndIndexArePassedThrough() {
        String url = ModrinthClient.buildSearchUrl("shaders", null, null, 20, 10,
                ModrinthClient.INDEX_DOWNLOADS);
        assertTrue(url, url.contains("query=shaders"));
        assertTrue(url, url.contains("index=downloads"));
        assertTrue(url, url.contains("offset=20"));
    }

    @Test
    public void searchEndpointIsUsed() {
        String url = ModrinthClient.buildSearchUrl("", null, null, 0, 20, null);
        assertTrue(url, url.startsWith("https://api.modrinth.com/v2/search?"));
    }
}
