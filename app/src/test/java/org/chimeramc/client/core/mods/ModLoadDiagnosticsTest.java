package org.chimeramc.client.core.mods;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ModLoadDiagnosticsTest {

    private static ModLoadDiagnostics.Record record(String id, String name, String version, String reason, String kind) {
        return new ModLoadDiagnostics.Record(id, name, version, reason, kind, 1_700_000_000_000L);
    }

    @Test
    public void roundTripPreservesAllFields() {
        List<ModLoadDiagnostics.Record> records = Arrays.asList(
                record("sprint", "Auto Sprint", "1.2.0", "dlopen failed: library not found", ModLoadDiagnostics.KIND_DLOPEN),
                record("zoom", "Zoom", "0.9", "Built for a different Minecraft version (this instance runs 1.21.90)",
                        ModLoadDiagnostics.KIND_INCOMPATIBLE));

        List<ModLoadDiagnostics.Record> parsed = ModLoadDiagnostics.parse(
                ModLoadDiagnostics.serialize(records));

        assertEquals(2, parsed.size());
        for (int i = 0; i < records.size(); i++) {
            assertEquals(records.get(i).modId, parsed.get(i).modId);
            assertEquals(records.get(i).modName, parsed.get(i).modName);
            assertEquals(records.get(i).version, parsed.get(i).version);
            assertEquals(records.get(i).reason, parsed.get(i).reason);
            assertEquals(records.get(i).kind, parsed.get(i).kind);
            assertEquals(records.get(i).timestampMs, parsed.get(i).timestampMs);
        }
    }

    @Test
    public void roundTripPreservesTabsAndNewlinesInReason() {
        List<ModLoadDiagnostics.Record> records = Collections.singletonList(
                record("mod", "Weird\tMod", "1.0", "line one\nline two\twith tab", ModLoadDiagnostics.KIND_SYMBOL));

        List<ModLoadDiagnostics.Record> parsed = ModLoadDiagnostics.parse(
                ModLoadDiagnostics.serialize(records));

        assertEquals(1, parsed.size());
        assertEquals("Weird\tMod", parsed.get(0).modName);
        assertEquals("line one\nline two\twith tab", parsed.get(0).reason);
    }

    @Test
    public void roundTripPreservesBackslashes() {
        List<ModLoadDiagnostics.Record> records = Collections.singletonList(
                record("mod", "Path", "1.0", "C:\\mods\\lib.so", ModLoadDiagnostics.KIND_DLOPEN));

        List<ModLoadDiagnostics.Record> parsed = ModLoadDiagnostics.parse(
                ModLoadDiagnostics.serialize(records));

        assertEquals(1, parsed.size());
        assertEquals("C:\\mods\\lib.so", parsed.get(0).reason);
    }

    @Test
    public void parseIgnoresBlankAndMalformedLines() {
        String content = "\n" + "\n" + "only-one-field\n";
        assertTrue(ModLoadDiagnostics.parse(content).isEmpty());
    }

    @Test
    public void parseOfNullOrEmptyIsEmpty() {
        assertTrue(ModLoadDiagnostics.parse(null).isEmpty());
        assertTrue(ModLoadDiagnostics.parse("").isEmpty());
        assertTrue(ModLoadDiagnostics.parse("   ").isEmpty());
    }

    @Test
    public void serializeOfNullIsEmpty() {
        assertTrue(ModLoadDiagnostics.serialize(null).isEmpty());
    }

    @Test
    public void longReasonIsTrimmedWhenRecording() {
        List<ModLoadDiagnostics.Record> source = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            source.add(record("mod" + i, "Mod " + i, "1.0", "reason", ModLoadDiagnostics.KIND_UNKNOWN));
        }
        String serialized = ModLoadDiagnostics.serialize(
                source.subList(0, 25));
        assertEquals(25, ModLoadDiagnostics.parse(serialized).size());
    }

    @Test
    public void nullFieldsBecomeEmptyStrings() {
        ModLoadDiagnostics.Record record = new ModLoadDiagnostics.Record(null, null, null, null, null, 5L);
        assertEquals("", record.modId);
        assertEquals("", record.modName);
        assertEquals("", record.version);
        assertEquals("", record.reason);
        assertEquals(ModLoadDiagnostics.KIND_UNKNOWN, record.kind);
    }

    @Test
    public void dlopenErrorClassifiedAsDlopen() {
        UnsatisfiedLinkError error = new UnsatisfiedLinkError(
                "dlopen failed: library \"libfoo.so\" not found");
        assertEquals(ModLoadDiagnostics.KIND_DLOPEN, ModLoadDiagnostics.Classifier.kindOf(error));
    }

    @Test
    public void missingSymbolClassifiedAsSymbol() {
        UnsatisfiedLinkError error = new UnsatisfiedLinkError(
                "ld: symbol not found: _missing_thing");
        assertEquals(ModLoadDiagnostics.KIND_SYMBOL, ModLoadDiagnostics.Classifier.kindOf(error));
    }

    @Test
    public void missingDependencyClassifiedAsMissingDependency() {
        RuntimeException error = new RuntimeException("Entry not found in cache");
        assertEquals(ModLoadDiagnostics.KIND_MISSING_DEPENDENCY, ModLoadDiagnostics.Classifier.kindOf(error));
    }

    @Test
    public void unknownErrorClassifiedAsUnknown() {
        assertEquals(ModLoadDiagnostics.KIND_UNKNOWN,
                ModLoadDiagnostics.Classifier.kindOf(new RuntimeException("boom")));
        assertEquals(ModLoadDiagnostics.KIND_UNKNOWN, ModLoadDiagnostics.Classifier.kindOf(null));
    }

    @Test
    public void classifierUsesRootCause() {
        RuntimeException wrapper = new RuntimeException("native load failed",
                new UnsatisfiedLinkError("dlopen failed: cannot open libbar.so"));
        assertEquals(ModLoadDiagnostics.KIND_DLOPEN, ModLoadDiagnostics.Classifier.kindOf(wrapper));
    }

    @Test
    public void describeFallsBackToClassNameWhenMessageMissing() {
        assertEquals("RuntimeException", ModLoadDiagnostics.Classifier.describe(new RuntimeException()));
        assertEquals("Mod failed to load", ModLoadDiagnostics.Classifier.describe(null));
    }

    @Test
    public void describeTrimsMessage() {
        assertEquals("boom", ModLoadDiagnostics.Classifier.describe(new RuntimeException("  boom  ")));
    }

    @Test
    public void recordSurvivesReasonContainingSeparatorSequence() {
        List<ModLoadDiagnostics.Record> records = Collections.singletonList(
                record("mod", "Name", "1.0", "abc\\ndef", ModLoadDiagnostics.KIND_UNKNOWN));
        List<ModLoadDiagnostics.Record> parsed = ModLoadDiagnostics.parse(
                ModLoadDiagnostics.serialize(records));
        assertEquals("abc\\ndef", parsed.get(0).reason);
    }

    @Test
    public void kindConstantsAreDistinct() {
        assertFalse(ModLoadDiagnostics.KIND_DLOPEN.equals(ModLoadDiagnostics.KIND_SYMBOL));
        assertFalse(ModLoadDiagnostics.KIND_INCOMPATIBLE.equals(ModLoadDiagnostics.KIND_UNKNOWN));
    }
}
