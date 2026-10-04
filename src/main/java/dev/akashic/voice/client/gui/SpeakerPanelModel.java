package dev.akashic.voice.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import dev.akashic.voice.voice.client.playback.SpeakerControls;

/** Client-only panel state. Names are display hints; control authority always uses the stream UUID. */
public final class SpeakerPanelModel {

    public static final class Row {

        public final UUID id;
        public final String name;
        public final int percent;
        public final boolean muted;

        private Row(UUID id, String name, SpeakerControls.State state) {
            this.id = id;
            this.name = name;
            this.percent = state.percent();
            this.muted = state.muted();
        }
    }

    private SpeakerControls controls;
    private SpeakerControls.Snapshot snapshot;
    private Map<UUID, String> names = Collections.emptyMap();
    private List<Row> filtered = Collections.emptyList();
    private List<Row> visible = Collections.emptyList();
    private String query = "";
    private UUID selected;
    private int page;
    private int pageSize = 5;

    public void refresh(SpeakerControls current, Map<UUID, String> displayNames, int requestedRows) {
        int nextSize = Math.max(1, Math.min(8, requestedRows));
        SpeakerControls.Snapshot next = current != null ? current.snapshot() : null;
        Map<UUID, String> resolved = new HashMap<>();
        if (next != null) {
            for (UUID id : next.speakers()) {
                resolved.put(id, label(id, displayNames != null ? displayNames.get(id) : null));
            }
        }
        boolean changed = controls != current || snapshot != next || nextSize != pageSize || !names.equals(resolved);
        if (controls != current) {
            selected = null;
            page = 0;
        }
        controls = current;
        snapshot = next;
        names = resolved;
        pageSize = nextSize;
        if (changed) rebuild();
    }

    public boolean connected() {
        return controls != null;
    }

    public List<Row> rows() {
        return visible;
    }

    public int pageIndex() {
        return page;
    }

    public int pageCount() {
        return Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
    }

    public int total() {
        return filtered.size();
    }

    public void filter(String text) {
        String value = text != null ? text : "";
        value = value.substring(0, Math.min(64, value.length()))
            .trim()
            .toLowerCase(Locale.ROOT);
        if (query.equals(value)) return;
        query = value;
        page = 0;
        rebuild();
    }

    public void turnPage(int direction) {
        int next = Math.max(0, Math.min(pageCount() - 1, page + Integer.signum(direction)));
        if (next == page) return;
        page = next;
        selected = null;
        updateVisible();
    }

    public boolean select(int row) {
        if (row < 0 || row >= visible.size()) return false;
        selected = visible.get(row).id;
        return true;
    }

    public Row selected() {
        for (Row row : filtered) if (row.id.equals(selected)) return row;
        return null;
    }

    public boolean setPercent(SpeakerControls current, int percent) {
        if (!editable(current) || percent < 0 || percent > 100) return false;
        return apply(
            current,
            percent,
            current.state(selected)
                .muted());
    }

    public boolean toggleMute(SpeakerControls current) {
        if (!editable(current)) return false;
        SpeakerControls.State state = current.state(selected);
        return apply(current, state.percent(), !state.muted());
    }

    public boolean reset(SpeakerControls current) {
        return editable(current) && apply(current, 100, false);
    }

    private boolean editable(SpeakerControls current) {
        return current != null && current == controls
            && selected != null
            && current.snapshot()
                .speakers()
                .contains(selected);
    }

    private boolean apply(SpeakerControls current, int percent, boolean muted) {
        if (!current.update(selected, percent, muted)) return false;
        snapshot = current.snapshot();
        rebuild();
        return true;
    }

    private void rebuild() {
        List<Row> next = new ArrayList<>();
        if (snapshot != null) {
            for (UUID id : snapshot.speakers()) {
                String name = names.get(id);
                if (name == null) name = label(id, null);
                if (name.toLowerCase(Locale.ROOT)
                    .contains(query)
                    || id.toString()
                        .contains(query)) {
                    next.add(new Row(id, name, snapshot.state(id)));
                }
            }
        }
        next.sort(
            Comparator.comparing((Row row) -> row.name.toLowerCase(Locale.ROOT))
                .thenComparing(row -> row.id.toString()));
        filtered = Collections.unmodifiableList(next);
        if (selected() == null) selected = null;
        page = Math.min(page, pageCount() - 1);
        updateVisible();
    }

    private void updateVisible() {
        int from = page * pageSize;
        visible = Collections
            .unmodifiableList(new ArrayList<>(filtered.subList(from, Math.min(filtered.size(), from + pageSize))));
    }

    private static String label(UUID id, String input) {
        if (input == null || input.trim()
            .isEmpty())
            return id.toString()
                .substring(0, 8);
        StringBuilder clean = new StringBuilder(64);
        for (int i = 0; i < input.length() && clean.length() < 64;) {
            int codePoint = input.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == 167 || Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT) continue;
            if (clean.length() + Character.charCount(codePoint) > 64) break;
            clean.appendCodePoint(codePoint);
        }
        String result = clean.toString()
            .trim();
        return result.isEmpty() ? id.toString()
            .substring(0, 8) : result;
    }
}
