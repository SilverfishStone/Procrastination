package com.workgame.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory history of all completed games this session.
 * Static so it survives screen transitions.
 */
public class StatsHistory {

    private static final List<GameStats> history = new ArrayList<>();

    public static void add(GameStats stats) {
        history.add(0, stats); // newest first
    }

    public static List<GameStats> getAll() {
        return Collections.unmodifiableList(history);
    }

    public static boolean isEmpty() {
        return history.isEmpty();
    }
}
