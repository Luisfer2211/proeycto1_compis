package com.compiscript.tac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Sequence of three-address instructions produced for one compilation unit. */
public final class TacProgram {

    private final List<String> lines = new ArrayList<>();
    private final boolean suppressed;
    private int maxLiveTemps;

    private TacProgram(boolean suppressed) {
        this.suppressed = suppressed;
    }

    public static TacProgram create() {
        return new TacProgram(false);
    }

    public static TacProgram suppressed() {
        return new TacProgram(true);
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    public void add(String line) {
        if (!suppressed && line != null && !line.isBlank()) {
            lines.add(line);
        }
    }

    public void setMaxLiveTemps(int maxLiveTemps) {
        this.maxLiveTemps = maxLiveTemps;
    }

    public int maxLiveTemps() {
        return maxLiveTemps;
    }

    public List<String> lines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean contains(String fragment) {
        return lines.stream().anyMatch(line -> line.contains(fragment));
    }

    public String render() {
        if (suppressed) {
            return "";
        }
        return String.join("\n", lines);
    }
}
