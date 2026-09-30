package com.compiscript.tac;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Allocates three-address temporaries and recycles them once their value has been consumed.
 * The next result temporary is requested only after operand temporaries are released, so
 * expressions such as {@code (a + b) * (c + d)} reuse {@code t0} instead of growing forever.
 */
public final class TempPool {

    private final PriorityQueue<Integer> free = new PriorityQueue<>();
    private final Set<Integer> liveIds = new HashSet<>();
    private int nextId;
    private int live;
    private int maxLive;

    public String acquire() {
        int id = free.isEmpty() ? nextId++ : free.remove();
        liveIds.add(id);
        live++;
        maxLive = Math.max(maxLive, live);
        return "t" + id;
    }

    public void release(String name) {
        Integer id = parse(name);
        if (id == null || !liveIds.remove(id)) {
            return;
        }
        free.add(id);
        live--;
    }

    public int maxLive() {
        return maxLive;
    }

    public int live() {
        return live;
    }

    private Integer parse(String name) {
        if (name == null || name.length() < 2 || name.charAt(0) != 't') {
            return null;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return null;
            }
        }
        return Integer.parseInt(name.substring(1));
    }
}
